#!/usr/bin/env python3
"""Discover pool compiler. Python 3.9+, no third-party dependencies."""
import argparse
import datetime as dt
import fcntl
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import tempfile
import urllib.request
from zoneinfo import ZoneInfo

LONDON = ZoneInfo('Europe/London')
LIMIT = 512 * 1024
ANGLES = ('explained by researchers', 'history documentary', 'field study',
          'museum lecture', 'how it works demonstration', 'traditional knowledge documentary')
REGIONS = ('Global', 'Africa', 'Asia', 'Europe', 'Americas', 'Oceania')


def normal(text):
    return re.sub(r'\W+', ' ', text.casefold()).strip()


def entry(topic, subject, angle, region):
    query = f'{subject} {angle}' + (f' {region}' if region != 'Global' else '')
    return dict(id=hashlib.sha256(normal(query).encode()).hexdigest()[:20],
                topic=topic, region=region, query=query)


def candidates(catalogue):
    # Region is an explicit search lens, not a claim about the subject's origin.
    return [entry(topic, subject, angle, region)
            for topic, subjects in sorted(catalogue.items()) for subject in subjects
            for angle in ANGLES for region in REGIONS]


def validate(items):
    if not isinstance(items, list) or not 8 <= len(items) <= 600:
        raise ValueError('pool must contain 8..600 entries')
    seen = set()
    for item in items:
        if not isinstance(item, dict):
            raise ValueError('entry must be an object')
        for key, maximum in [('id', 64), ('topic', 60), ('region', 40), ('query', 240)]:
            value = item.get(key)
            if not isinstance(value, str) or not value.strip() or len(value) > maximum or any(ord(c) < 32 for c in value):
                raise ValueError('invalid ' + key)
        identity = normal(item['query'])
        if item['id'] in seen or identity in seen:
            raise ValueError('duplicate entry')
        seen.update((item['id'], identity))
    if len({i['topic'] for i in items}) < 8:
        raise ValueError('insufficient topic diversity')
    return items


def generate(catalogue, now, seed='adnada-v2', size=216, extra=()):
    local = now.astimezone(LONDON)
    slot = local.date().toordinal() * 4 + local.hour // 6
    grouped = {}
    seen = set()
    for item in candidates(catalogue) + list(extra):
        identity = normal(item['query'])
        if identity in seen:
            continue
        seen.add(identity)
        grouped.setdefault(item['topic'], {})[identity] = item
    topics = sorted(grouped)
    if size < 8 or size > 600 or len(topics) < 8:
        raise ValueError('invalid selection dimensions')
    picked = []
    ordered_topics = topics[slot % len(topics):] + topics[:slot % len(topics)]
    pages = {}
    for topic in ordered_topics:
        ordered = sorted(grouped[topic].values(), key=lambda i: hashlib.sha256(
            (seed + i['id']).encode()).digest())
        per_topic = (size + len(topics) - 1) // len(topics)
        offset = (slot * per_topic) % len(ordered)
        pages[topic] = ordered[offset:] + ordered[:offset]
    # Round robin subjects, allowing small novelty categories without duplicate padding.
    for row in range(max(map(len, pages.values()))):
        for topic in ordered_topics:
            if row < len(pages[topic]) and len(picked) < size:
                picked.append(pages[topic][row])
        if len(picked) == size:
            break
    if len(picked) != size:
        raise ValueError('insufficient unique directions')
    validate(picked)
    payload = dict(schema_version=2, generated_at=local.replace(hour=(local.hour // 6)*6,
        minute=0, second=0, microsecond=0).isoformat(), seed=seed, entries=picked)
    payload['revision'] = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()[:20]
    return payload


def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.' + path.name, dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(value, stream, ensure_ascii=False, sort_keys=True, indent=2)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(name, 0o600)
        os.replace(name, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def replenish(config, state, state_path, now, fresh_count, opener=urllib.request.urlopen):
    """Fail closed: reserve a whole request before I/O; never retry a lost response."""
    enabled = config.get('daily_calls', 0)
    if enabled not in (0, 1):
        raise ValueError('daily_calls must be zero or one')
    day = now.astimezone(LONDON).date().isoformat()
    if not enabled or fresh_count >= config.get('replenish_below', 100) or state.get('attempt_day', '') >= day:
        return []
    key = os.environ.get('OPENROUTER_API_KEY')
    model = config.get('model', '')
    if not key or not model:
        return []
    # Hard conservative per-attempt reservation; unknown usage stays charged.
    reserve = 0.01
    budget = min(float(config.get('lifetime_budget_usd', 0.25)), 1.0)
    spent = float(state.get('reserved_usd', 0))
    if not 0 <= spent <= budget - reserve:
        return []
    state.update(attempt_day=day, reserved_usd=round(spent + reserve, 6))
    atomic_json(state_path, state)
    prompt = ('Return only a JSON array of 8 novel educational YouTube search directions. '
              'Each object has topic, region (Global), query. '
              'Use 8 distinct subjects, specific obscure ideas, no clickbait or personalities. '
              'Explore connections between crafts, ecology, language, history and science. '
              'No links, markdown or instructions. Seed: ' + day)
    request = urllib.request.Request('https://openrouter.ai/api/v1/chat/completions',
        data=json.dumps(dict(model=model, messages=[dict(role='user', content=prompt)],
            max_tokens=512, temperature=0, seed=int(day.replace('-', '')),
            provider=dict(require_parameters=True, allow_fallbacks=False,
                max_price=dict(prompt=0.5, completion=1, request=0)),
            usage=dict(include=True))).encode(),
        headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    try:
        with opener(request, timeout=20) as response:
            raw = response.read(LIMIT + 1)
        if len(raw) > LIMIT:
            raise ValueError('response too large')
        body = json.loads(raw)
        usage = body.get('usage', {})
        # Persist actual provider usage, including on invalid content; never log keys/prompts.
        state['last_usage'] = {k: usage.get(k) for k in ('prompt_tokens', 'completion_tokens', 'cost')}
        atomic_json(state_path, state)
        logging.info('OpenRouter usage %s', json.dumps(state['last_usage']))
        items = json.loads(body['choices'][0]['message']['content'])
        if not isinstance(items, list):
            raise ValueError('expected array')
        for item in items:
            if not isinstance(item, dict) or not isinstance(item.get('query'), str):
                raise ValueError('invalid novelty')
            # Provider IDs are not trusted or stable across days.
            item['id'] = hashlib.sha256(normal(item['query']).encode()).hexdigest()[:20]
        return validate(items)
    except Exception as error:
        logging.warning('Novelty unavailable: %s', type(error).__name__)
        return []


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True, help='private state directory (not served)')
    parser.add_argument('--catalogue', type=Path, default=Path(__file__).with_name('catalogue.json'))
    parser.add_argument('--config', type=Path)
    parser.add_argument('--at', help='ISO timestamp for deterministic replay')
    parser.add_argument('--seed', default='adnada-v2')
    args = parser.parse_args()
    args.directory.mkdir(parents=True, exist_ok=True)
    with (args.directory / 'curator.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        now = dt.datetime.fromisoformat(args.at) if args.at else dt.datetime.now(dt.timezone.utc)
        if now.tzinfo is None:
            raise ValueError('--at requires a timezone')
        catalogue = json.loads(args.catalogue.read_text())
        config = json.loads(args.config.read_text()) if args.config else {}
        state_path = args.directory / 'state.json'
        # Corrupt state is an error, never an invitation to reset the spend budget.
        state = json.loads(state_path.read_text()) if state_path.exists() else {}
        extras = state.get('extras', [])
        recent = set(state.get('recent', []))
        fresh = sum(i['id'] not in recent for i in candidates(catalogue))
        novelty = replenish(config, state, state_path, now, fresh)
        if novelty:
            extras = (extras + novelty)[-128:]
        pool = generate(catalogue, now, args.seed, extra=extras)
        state.update(extras=extras, recent=(state.get('recent', []) + [i['id'] for i in pool['entries']])[-2000:])
        atomic_json(state_path, state)
        atomic_json(args.directory / 'pool.json', pool)
        logging.info('revision=%s entries=%d model calls default=0', pool['revision'], len(pool['entries']))


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
    main()
