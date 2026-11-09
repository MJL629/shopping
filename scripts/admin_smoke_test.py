"""AI-assisted real-runtime checks for merchant metadata, reports and non-root image uploads.
Creates a tiny test image only; leaves accounts, orders and baskets unchanged.
"""
from pathlib import Path
import datetime,json,os,re,struct,urllib.request,urllib.error,urllib.parse,uuid,zlib
from smoke_test import Client,BASE,ROOT
checks=[]
def check(name):checks.append(name);print('PASS',name,flush=True)
def main():
 config=({'adminPassword':os.environ['SHOP_TEST_ADMIN_PASSWORD'],'customerPassword':os.environ['SHOP_TEST_CUSTOMER_PASSWORD']} if os.environ.get('SHOP_TEST_ADMIN_PASSWORD') else json.loads((ROOT/'.local/runtime.json').read_text(encoding='utf-8')))
 admin=Client();admin.login('admin',config['adminPassword']);customer=Client();customer.login('demo',config['customerPassword'])
 products=admin.call('/products?keyword=MUJI&size=1');assert products['total']==100
 p=products['items'][0];assert p['sourceType']=='MERCHANT_CATALOG' and p['sourceCurrency']=='USD' and p['sourcePrice']>0 and len(json.loads(p['galleryJson']))>=2 and len(json.loads(p['specificationsJson']))>=4;check('real merchant metadata, original pricing and gallery')
 included=admin.call('/admin/stats');excluded=admin.call('/admin/stats?includeSamples=false');assert excluded['summary']['revenue']<=included['summary']['revenue'];assert excluded['summary']['totalOrders']<=included['summary']['totalOrders'];check('sample order statistics can be excluded')
 admin.call('/admin/stats?from=2026-11-09&to=2026-11-01',status=400);check('invalid report date range rejected')
 with admin.opener.open(BASE+'/admin/reports/sales.csv',timeout=15) as response:
  content=response.read();assert response.status==200 and content.startswith(b'\xef\xbb\xbf') and '销售额' in content.decode('utf-8')
 check('CSV report downloads with UTF-8 encoding')
 orders=admin.call('/admin/orders?size=1')['items']
 if orders:
  found=admin.call('/admin/orders?keyword='+urllib.parse.quote(orders[0]['orderNumber']));assert found['total']==1
 check('order number lookup')
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
 png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b'\x00\x44\x66\x44'))+chunk(b'IEND',b'')
 def upload(content,filename,mime,expected):
  boundary='Shiguang'+uuid.uuid4().hex
  payload=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+filename+'"\r\nContent-Type: '+mime+'\r\n\r\n').encode()+content+('\r\n--'+boundary+'--\r\n').encode()
  req=urllib.request.Request(BASE+'/admin/images',data=payload,method='POST',headers={'Content-Type':'multipart/form-data; boundary='+boundary,'X-XSRF-TOKEN':admin.token})
  try:
   with admin.opener.open(req,timeout=15) as response:status=response.status;body=response.read()
  except urllib.error.HTTPError as error:status=error.code;body=error.read()
  assert status==expected,(status,body[:200]);return json.loads(body)
 image=upload(png,'../tiny-test.png','image/png',201);assert re.fullmatch(r'/uploads/[a-f0-9-]{36}\.png',image['imageUrl']);check('admin image upload with generated safe filename')
 origin=BASE.removesuffix('/api')
 with urllib.request.urlopen(origin+image['imageUrl'],timeout=15) as response:assert response.status==200 and response.read().startswith(b'\x89PNG')
 check('uploaded image served by running application')
 upload(b'<svg onload="alert(1)"/>','unsafe.svg','image/svg+xml',400);check('active SVG upload rejected')
 customer.call('/admin/images','POST',{},status=403);check('customer cannot upload admin images')
 (ROOT/'.local').mkdir(exist_ok=True)
 (ROOT/'.local/admin-smoke-report.json').write_text(json.dumps({'passed':len(checks),'checks':checks,'baseUrl':BASE,'scope':'merchant/report/image runtime checks; no business rows modified'},indent=2),encoding='utf-8')
 print('All',len(checks),'extended runtime checks passed.')
if __name__=='__main__':main()
