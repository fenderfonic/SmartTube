import datetime as dt
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import curator
import serve

CAT = json.loads(Path(curator.__file__).with_name('catalogue.json').read_text())
NOW = dt.datetime(2026, 11, 14, tzinfo=dt.timezone.utc)


class PoolTests(unittest.TestCase):
    def test_reproducible_and_rotates_without_repeating_last_day(self):
        first = curator.generate(CAT, NOW)
        self.assertEqual(first, curator.generate(dict(reversed(list(CAT.items()))), NOW))
        seen = set()
        for slot in range(8):
            pool = curator.generate(CAT, NOW + dt.timedelta(hours=6 * slot))
            queries = {curator.normal(e['query']) for e in pool['entries']}
            self.assertEqual(216, len(queries))
            self.assertFalse(seen & queries)
            self.assertEqual(18, len({e['topic'] for e in pool['entries']}))
            self.assertEqual(6, len({e['region'] for e in pool['entries']}))
            for topic in CAT:
                self.assertEqual(6, len({e['region'] for e in pool['entries'] if e['topic'] == topic}))
            seen |= queries
        self.assertNotEqual(first, curator.generate(CAT, NOW, seed='other'))

    def test_london_summer_and_winter_slots(self):
        for timestamp in ('2026-03-29T05:00:00+00:00', '2026-10-25T06:00:00+00:00'):
            at = dt.datetime.fromisoformat(timestamp)
            pool = curator.generate(CAT, at)
            self.assertEqual(6, dt.datetime.fromisoformat(pool['generated_at']).hour)
            self.assertEqual(pool, curator.generate(CAT, at + dt.timedelta(minutes=45)))

    def test_novelty_with_small_topics_never_duplicates(self):
        extra = [curator.entry('New subject', 'obscure process', 'lecture', 'Global')]
        pool = curator.generate(CAT, NOW, extra=extra)
        self.assertEqual(216, len(pool['entries']))
        curator.validate(pool['entries'])

    def test_schema_rejects_duplicates_and_control_characters(self):
        entries = curator.generate(CAT, NOW)['entries']
        with self.assertRaises(ValueError):
            curator.validate(entries + entries[:1])
        entries[0]['query'] = 'bad\nquery'
        with self.assertRaises(ValueError):
            curator.validate(entries)

    def test_atomic_failure_keeps_last_good(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'pool.json'
            curator.atomic_json(path, {'good': True})
            with patch('curator.os.replace', side_effect=OSError('disk')):
                with self.assertRaises(OSError):
                    curator.atomic_json(path, {'bad': True})
            self.assertEqual({'good': True}, json.loads(path.read_text()))
            self.assertEqual(['pool.json'], [p.name for p in path.parent.iterdir()])

    def test_llm_disabled_and_sufficient_pool_cost_nothing(self):
        with tempfile.TemporaryDirectory() as folder:
            state = {}
            path = Path(folder) / 'state.json'
            with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'test'}):
                for config in ({}, {'daily_calls': 1, 'model': 'test'}):
                    self.assertEqual([], curator.replenish(config, state, path, NOW, 500,
                        opener=lambda *a, **k: self.fail('unexpected call')))
            self.assertEqual({}, state)

    def test_llm_failure_reserves_budget_and_no_retry_same_day(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'state.json'
            state = {}
            calls = []
            def fail(request, **kwargs):
                calls.append(json.loads(request.data))
                raise TimeoutError()
            config = dict(daily_calls=1, model='test', lifetime_budget_usd=0.02)
            with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'test'}):
                curator.replenish(config, state, path, NOW, 0, fail)
                curator.replenish(config, state, path, NOW, 0, fail)
                state = json.loads(path.read_text())
                curator.replenish(config, state, path, NOW + dt.timedelta(days=1), 0, fail)
                curator.replenish(config, state, path, NOW + dt.timedelta(days=2), 0, fail)
            self.assertEqual(2, len(calls))
            self.assertEqual(512, calls[0]['max_tokens'])
            self.assertEqual(0, calls[0]['provider']['max_price']['request'])
            self.assertEqual(0.02, state['reserved_usd'])

    def test_llm_logs_actual_usage_even_when_content_invalid(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, limit):
                return json.dumps(dict(usage=dict(prompt_tokens=12, completion_tokens=5, cost=0.0001),
                    choices=[dict(message=dict(content='not JSON'))])).encode()
        with tempfile.TemporaryDirectory() as folder:
            state = {}
            path = Path(folder) / 'state.json'
            with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'test'}):
                self.assertEqual([], curator.replenish(dict(daily_calls=1, model='test'), state,
                    path, NOW, 0, lambda *a, **k: Response()))
            self.assertEqual(0.0001, json.loads(path.read_text())['last_usage']['cost'])

    def test_llm_valid_novelty_has_canonical_ids_and_replays(self):
        items = [dict(topic=f'Novel topic {n}', region='Global', query=f'Obscure process {n} lecture')
                 for n in range(8)]
        class Response:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, limit):
                return json.dumps(dict(usage=dict(prompt_tokens=100, completion_tokens=200, cost=0.00025),
                    choices=[dict(message=dict(content=json.dumps(items)))])).encode()
        with tempfile.TemporaryDirectory() as folder:
            state = {}
            with patch.dict(os.environ, {'OPENROUTER_API_KEY': 'test'}):
                result = curator.replenish(dict(daily_calls=1, model='test'), state,
                    Path(folder) / 'state.json', NOW, 0, lambda *a, **k: Response())
            self.assertEqual(8, len(result))
            self.assertEqual(8, len({i['id'] for i in result}))
            for item in result:
                self.assertEqual(curator.hashlib.sha256(curator.normal(item['query']).encode()).hexdigest()[:20], item['id'])
            self.assertEqual(curator.generate(CAT, NOW, extra=result),
                             curator.generate(CAT, NOW, extra=json.loads(json.dumps(result))))

    def test_cross_topic_dedup_and_malformed_items(self):
        item = curator.candidates(CAT)[0]
        duplicate = dict(item, topic='Other topic', id='provider-chosen-id')
        curator.validate(curator.generate(CAT, NOW, extra=[duplicate])['entries'])
        with self.assertRaises(ValueError):
            curator.validate(['not an object'] * 8)

    def test_private_read_only_server_exposes_only_pool(self):
        self.assertFalse(serve.private_address('0.0.0.0'))
        self.assertFalse(serve.private_address('8.8.8.8'))
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'pool.json'
            curator.atomic_json(path, curator.generate(CAT, NOW))
            class Socket:
                def __init__(self, request):
                    self.request = io.BytesIO(request.encode())
                    self.response = bytearray()
                def makefile(self, *args): return self.request
                def sendall(self, data): self.response.extend(data)
            for method, route, status in [('GET', '/pool.json', 200), ('GET', '/', 404),
                    ('GET', '/state.json', 404), ('GET', '/../state.json', 404),
                    ('GET', '/pool.json?x=1', 404), ('POST', '/pool.json', 501)]:
                sock = Socket(f'{method} {route} HTTP/1.0\r\nHost: localhost\r\n\r\n')
                serve.handler(path)(sock, ('127.0.0.1', 1234), object())
                self.assertIn(f'HTTP/1.0 {status} '.encode(), sock.response)
                if status == 200:
                    payload = bytes(sock.response).split(b'\r\n\r\n', 1)[1]
                    self.assertEqual(216, len(json.loads(payload)['entries']))
            sock = Socket('GET /pool.json HTTP/1.0\r\n\r\n')
            serve.handler(path)(sock, ('8.8.8.8', 1234), object())
            self.assertIn(b'HTTP/1.0 403 ', sock.response)



if __name__ == '__main__':
    unittest.main()
