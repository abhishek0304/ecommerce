"""Read-only public deployment evidence. A generated manifest is not deployment proof."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import socket
import ssl
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def verify(origin):
    parsed = urllib.parse.urlsplit(origin)
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password
            or parsed.path not in ['', '/'] or parsed.query or parsed.fragment or parsed.port not in [None, 443]):
        raise ValueError('Provide an HTTPS origin on port 443, without credentials, path, query or fragment')
    host = parsed.hostname
    origin = 'https://' + parsed.netloc
    report = {'checkedAt': datetime.now(timezone.utc).isoformat(), 'origin': origin, 'checks': [],
              'scope': 'Public DNS, trusted TLS, HTTP redirect, storefront and catalog only. Does not prove checkout, provider delivery, database backups or release identity.'}

    def record(name, action):
        try:
            details = action()
            report['checks'].append({'name': name, 'passed': True, 'details': details})
        except Exception as exc:
            report['checks'].append({'name': name, 'passed': False, 'error': str(exc)})

    def dns():
        return {'addresses': sorted({row[4][0] for row in socket.getaddrinfo(host,443,type=socket.SOCK_STREAM)})}

    def tls():
        with socket.create_connection((host,443),timeout=15) as connection:
            with ssl.create_default_context().wrap_socket(connection,server_hostname=host) as secure:
                cert=secure.getpeercert()
                return {'protocol':secure.version(),'expiresAt':cert.get('notAfter'),'subjectAlternativeNames':cert.get('subjectAltName',[])}

    # Explicit openers avoid accepting a proxy-served response as origin evidence.
    https = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    http = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def redirect():
        try:
            with http.open('http://'+parsed.netloc+'/',timeout=15) as response:
                raise ValueError('HTTP served status '+str(response.status)+' instead of redirecting to HTTPS')
        except urllib.error.HTTPError as response:
            location=urllib.parse.urljoin('http://'+parsed.netloc+'/',response.headers.get('Location',''))
            target=urllib.parse.urlsplit(location)
            if response.code not in [301,302,307,308] or target.scheme!='https' or target.netloc!=parsed.netloc:
                raise ValueError('Expected same-host HTTPS redirect; received '+str(response.code))
            return {'status':response.code,'location':location}

    def get(path, content_type, catalog=False):
        request=urllib.request.Request(origin+path,headers={'Accept':content_type,'User-Agent':'EcommerceDeploymentVerifier/1.0'})
        with https.open(request,timeout=20) as response:
            if response.status!=200 or content_type not in response.headers.get('Content-Type',''):
                raise ValueError('Unexpected status or content type: '+str(response.status))
            data=response.read(2_000_001)
            if len(data)>2_000_000: raise ValueError('Response exceeded verification size limit')
            if catalog:
                body=json.loads(data)
                if not isinstance(body,dict) or not isinstance(body.get('content'),list):
                    raise ValueError('Response is not a paginated product catalog')
            elif b'<html' not in data.lower():
                raise ValueError('Storefront response does not contain HTML')
            return {'status':response.status,'contentType':response.headers.get('Content-Type'),
                    'traceId':response.headers.get('X-Trace-Id')}

    record('Public DNS resolution',dns)
    record('Trusted hostname-valid TLS certificate',tls)
    record('HTTP redirects to same-host HTTPS',redirect)
    record('HTTPS storefront',lambda:get('/index.html','text/html'))
    record('HTTPS gateway catalog',lambda:get('/api/v1/products?page=0&size=1','application/json',True))
    report['passed']=all(check['passed'] for check in report['checks'])
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url',required=True,help='Public HTTPS origin, e.g. https://shop.your-domain.tld')
    parser.add_argument('--output',type=Path,required=True,help='Evidence JSON file (no credentials or response bodies)')
    args=parser.parse_args()
    try:
        report=verify(args.url)
    except ValueError as exc:
        parser.error(str(exc))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    for check in report['checks']:
        print(('PASS' if check['passed'] else 'FAIL')+': '+check['name'])
    print('Evidence: '+str(args.output))
    raise SystemExit(0 if report['passed'] else 1)
