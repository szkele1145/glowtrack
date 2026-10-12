import os, json
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'audio', 'cache')
CACHE = os.path.abspath(CACHE)
bad = 0; total = 0
for fn in sorted(os.listdir(CACHE)):
    if not fn.endswith('.json'):
        continue
    total += 1
    p = os.path.join(CACHE, fn)
    try:
        json.load(open(p, encoding='utf-8'))
    except Exception as e:
        bad += 1
        print('BAD:', fn, type(e).__name__, str(e)[:80])
print(f'total={total} bad={bad}')
