"""Independent controller inventory and generated artifact checks (no service calls)."""
import json
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/postman'
collection=json.loads((OUT/'Ecommerce.postman_collection.json').read_text(encoding='utf-8'))
environment=json.loads((OUT/'Ecommerce.local.postman_environment.json').read_text(encoding='utf-8'))
manifest=json.loads((OUT/'endpoint-coverage.json').read_text(encoding='utf-8'))
def canonical(path):
    path=path.split('?',1)[0]
    path=re.sub(r'\{\{[^}]+\}\}',':id',path)
    path=re.sub(r'\{[^}]+\}',':id',path)
    path=re.sub(r'/[0-9a-f]{8}-[0-9a-f-]{27,}(?=/|$)','/:id',path)
    path=re.sub(r'/\d+(?=/|$)','/:id',path)
    return path.replace('/SAVE10','/:id')

actual=set()
for module in ['user-service','product-service','cart-service','order-service','notification-service','api-gateway','config-server','service-registry']:
    for path in (ROOT/module).rglob('*.java'):
        if '/src/main/java/' not in path.as_posix(): continue
        source=path.read_text(encoding='utf-8')
        if '@RestController' not in source: continue
        before=source.split('class ',1)[0]
        prefix=re.search(r'@RequestMapping\(([^)]*)\)',before)
        prefixes=re.findall(r'"([^"]+)"',prefix.group(1)) if prefix else ['']
        for mapping in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\(([^)]*)\))?',source):
            suffixes=re.findall(r'"([^"]*)"',mapping.group(2) or '') or ['']
            for prefix in prefixes:
                for suffix in suffixes: actual.add((mapping.group(1).upper(),canonical(prefix+suffix)))
documented={(x['method'],canonical(x['path'])) for x in manifest}
assert not actual-documented, f'Missing endpoints: {actual-documented}'
assert not documented-actual, f'Stale endpoints: {documented-actual}'
env={x['key']:x['value'] for x in environment['values']}
items=[item for folder in collection['item'] for item in folder['item']]
assert len({x['id'] for x in items})==len(items), 'Duplicate request IDs'
assert len({x['name'] for x in items})==len(items), 'Duplicate names break selection'
assert collection['info']['schema'].endswith('/v2.1.0/collection.json')
assert all(x['request']['description'] and x['response'] for x in items)
referenced=set()
for item in items:
    request=item['request']
    assert request['method'] in ['GET','POST','PUT','PATCH','DELETE']
    assert isinstance(request['url'],str) and request['url'].startswith('{{')
    assert any(x['listen']=='test' for x in item['event'])
    serialized=json.dumps(request)
    referenced.update(re.findall(r'\{\{([^}]+)\}\}',serialized))
    body=request.get('body',{}).get('raw')
    if body:
        # Validate JSON after deterministic stand-in substitution, including numeric values.
        body=re.sub(r'\{\{[^}]+\}\}','1',body)
        json.loads(body)
    for example in item['response']:
        assert example['originalRequest']==request
        if example['body'] and example['_postman_previewlanguage']=='json': json.loads(example['body'])
        if example['code']==204: assert example['body']==''
assert not referenced-set(env), f'Undefined environment variables: {referenced-set(env)}'
for key,value in env.items():
    if any(word in key.lower() for word in ['token','signature','servicekey','otp']): assert not value, f'Secret default: {key}'
assert env['enableAdminFlow']=='false' and env['referenceRequest']==''
assert len(items)==141 and len(actual)==76
print(f'PASS: {len(actual)} current controller routes/aliases fully covered; {len(items)} requests with examples; all request variables declared; no embedded credentials.')
