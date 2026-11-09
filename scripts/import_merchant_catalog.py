"""AI-assisted factual catalog snapshot from MUJI USA's public stationery collection.
Does not copy merchant marketing descriptions. Prices/stock in this shop remain fictional.
Downloads are bounded, cached locally, and need no authentication.
"""
from pathlib import Path
import concurrent.futures,datetime,hashlib,html,json,re,time,urllib.request,argparse
ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT/'.local/merchant-sources';CACHE.mkdir(exist_ok=True)
URL='https://www.muji.us/collections/stationery/products.json?limit=150'
HEADERS={'User-Agent':'ShiguangCourseProject/1.0 educational source attribution'}
CATEGORIES={'Notebook':'笔记手账','Pens':'书写工具','Pencils & Erasers':'书写工具','Markers & Highlighters':'书写工具','Pen Cases':'随身收纳','Folders & Binders':'文件整理','File Boxes':'文件整理','Albums':'纸品与相册','Envelopes & Cards':'卡片礼物','Gift Wrapping':'卡片礼物','Stickynotes & Memopads':'便签标记','Desk Accessories':'桌面文具','Looseleaf':'笔记手账','Polypropylene Storage Case':'桌面收纳','Card Holders':'随身收纳'}
NAMES={'Notebook':'笔记本','Pens':'书写笔','Pencils & Erasers':'铅笔用品','Markers & Highlighters':'记号笔','Pen Cases':'笔袋','Folders & Binders':'文件夹','File Boxes':'文件整理盒','Albums':'相册','Envelopes & Cards':'纸品卡片','Gift Wrapping':'礼品包装','Stickynotes & Memopads':'便签','Desk Accessories':'桌面文具','Looseleaf':'活页纸','Polypropylene Storage Case':'桌面收纳盒','Card Holders':'卡片收纳'}
SPECIFIC=[('Cat Sticky','猫咪便签'),('Gel Ink Ballpoint','中性笔套装'),('Honeycomb Paper Pop-Up','蜂窝纸立体卡片'),('Message Card','留言卡片'),('Marking Sticker','半透明标记贴'),('No-Iron Sticker','免熨标签贴'),('2-Hole File Holder','双孔文件夹'),('Paper Binder','纸质活页夹'),('Hardcover Album','硬壳相册'),('Nylon Mesh','尼龙网眼收纳'),('Double Binder Clip','长尾夹套装'),('Paper Clip','回形针套装'),('Double-Sided Ruler','双面刻度尺'),('Scissors','防粘剪刀'),('Mechanical Pencil Leads','自动铅笔芯'),('Wooden Mechanical Pencil','木杆自动铅笔'),('Wire-bound Sketchbook','线圈素描本'),('Double Ring','双线圈笔记本'),('Cable Case','带支架线缆收纳盒'),('Tape Dispenser','胶带座'),('Masking Tape','纸胶带'),('Clear Pocket','透明内页补充装')]
def fetch(url):
 for attempt in range(3):
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers=HEADERS),timeout=25) as response:
    content=response.read(4000001)
    if len(content)>4000000:raise ValueError('Image exceeds snapshot limit')
    return content,response.headers.get_content_type()
  except Exception:
   if attempt==2:raise
   time.sleep(attempt+1)
def sql(value):
 if value is None:return 'NULL'
 if isinstance(value,(int,float)):return str(value)
 parts=str(value).split(chr(92))
 return "CONCAT("+",CHAR(92),".join("'"+part.replace("'","''")+"'" for part in parts)+")" if len(parts)>1 else "'"+str(value).replace("'","''")+"'"
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--migration-version',type=int);args=parser.parse_args()
 migration=ROOT/f'backend/src/main/resources/db/migration/V{args.migration_version}__curated_merchant_stationery.sql' if args.migration_version else None
 if migration and migration.exists():raise RuntimeError('Never overwrite an existing Flyway migration. Choose a new version.')
 source=CACHE/'muji-stationery.json'
 if not source.exists():source.write_bytes(fetch(URL)[0])
 products=json.loads(source.read_text(encoding='utf-8'))['products']
 curated=[p for p in products if p['product_type'] in CATEGORIES and len(p['images'])>=2 and any(v['available'] for v in p['variants'])][:100]
 assert len(curated)==100,'Review source changes before importing.'
 destination=ROOT/'frontend/public/images/merchant';destination.mkdir(parents=True,exist_ok=True)
 observed=datetime.datetime.fromtimestamp(source.stat().st_mtime,datetime.timezone.utc).isoformat()
 def convert(entry):
  index,p=entry;gallery=[];image_sources=[]
  for position,image in enumerate(p['images'][:3]):
   image_url=image['src']+('&' if '?' in image['src'] else '?')+'width=800'
   base=f'muji-{p["id"]}-{position}'
   image_path=next((candidate for candidate in [destination/(base+ext) for ext in ('.jpg','.png','.webp')] if candidate.exists()),destination/(base+'.jpg'))
   if not image_path.exists():
    content,mime=fetch(image_url)
    if mime not in ('image/jpeg','image/png','image/webp'):raise ValueError('Unexpected image format')
    extension={'image/jpeg':'.jpg','image/png':'.png','image/webp':'.webp'}[mime]
    image_path=destination/(base+extension);image_path.write_bytes(content)
   gallery.append('/images/merchant/'+image_path.name);image_sources.append(image_url)
  variant=next(v for v in p['variants'] if v['available'])
  # Extract non-creative size facts from the title, not the source marketing copy.
  sizes=re.findall(r'\b(?:[ABF][0-9]|\d+(?:\.\d+)?\s*(?:mm|cm|Sheets|Pack|Rows?|Rings?|Holes?))\b',p['title'],re.I)
  type_name=p['product_type'];translated=next((name for term,name in SPECIFIC if term.lower() in p['title'].lower()),NAMES[type_name])
  detail=' / '.join(dict.fromkeys(sizes))
  translations=[('Polypropylene File Box','聚丙烯文件盒'),('High Transparency Photo Album','高透明相册'),('Bamboo Paper Petit Envelope','竹纸迷你信封'),('Paper Window Gift Bag','开窗礼品纸袋'),('Self-Standing Card File','立式卡片收纳册'),('Viewable-At-Once Card File','一览式卡片册'),('Perforated Checklist','待办清单便签卷'),('Perforated Fastening Tape','易撕捆扎带'),('Full Adhesive Deadline','截止日期便签'),('Sticky Square Index','方形索引贴'),('Arrow Sticky Tab','箭头索引贴'),('Sticky Page Marker','页码索引贴'),('Full Adhesive Label','全胶标签便签'),('Full Adhesive Square','全胶方形便签'),('Stick-On-Edges Checklist','侧边清单便签'),('Double-Sided Sticky','双面便签套装'),('Adhesive Cellophane','透明胶带'),('Soft Film Clear Folder','软质透明文件夹'),('Nylon Mesh Bag Organizer','尼龙网眼包中包'),('Polypropylene Card Holder','聚丙烯卡片夹'),('Stretchy Polyester Card Holder','弹力卡套'),('Oil-Based Ink Twin','油性双头记号笔'),('Polypropylene Case','聚丙烯收纳盒')]
  translated=next((cn for en,cn in translations if en.lower() in p['title'].lower()),translated)
  qualifiers=[]
  for en,cn in [('Dark Gray','深灰'),('Dark Grey','深灰'),('Beige','米色'),('Pink','粉色'),('Yellow','黄色'),('Blue','蓝色'),('Orange','橘色'),('Burgundy','酒红'),('Natural','自然色'),('Thin','薄款'),('Wide','宽款'),('Tall','高款'),('Small','小号'),('Large','大号'),('Polar Bear','北极熊'),('Giant Panda','大熊猫'),('Red Panda','小熊猫'),('Dot Grid','点阵')]:
   if en.lower() in p['title'].lower():qualifiers.append(cn)
  name='MUJI '+translated+(' · '+ ' '.join([detail]+qualifiers).strip() if detail or qualifiers else '')
  specs=[{'label':'品牌','value':'MUJI 无印良品'},{'label':'原始品名','value':p['title']},{'label':'来源商品编号','value':variant['sku'] or str(p['id'])},{'label':'原站品类','value':type_name}]
  if detail:specs.append({'label':'标题标注规格','value':detail})
  for option in p['options']:
   if option['name']!='Title':specs.append({'label':'原站选项 · '+option['name'],'value':' / '.join(option['values'])[:300]})
  specs.append({'label':'本店规格说明','value':'按原站商品资料展示；本课程订单不选择变体，不产生实物交易。'})
  source_price=float(variant['price']);price=max(5,int(round(source_price*7/5)*5)-1)
  description='这是一款来自 MUJI 美国官方商店的'+translated+'资料选物，适合学习、书写与整理场景。'+('原始品名标注：'+detail+'。' if detail else '')+'具体选项与商品编号见规格表；原站美元价格与采集日期单独展示。本店人民币价格、库存及付款均为课程演示设置。'
  return {'id':4000+index,'name':name[:100],'originalName':p['title'],'category':CATEGORIES[type_name],'price':price,'stock':30+index%35,'imageUrl':gallery[0],'tagline':'学习有序，日常顺手','description':description,'brand':'MUJI','sourceType':'MERCHANT_CATALOG','sourceUrl':'https://www.muji.us/products/'+p['handle'],'galleryJson':json.dumps(gallery,ensure_ascii=False),'specificationsJson':json.dumps(specs,ensure_ascii=False),'sourcePrice':source_price,'sourceCurrency':'USD','sourceObservedAt':observed,'merchantName':'MUJI USA 官方商店','sourceProductId':str(p['id']),'sourceVariantSku':variant['sku'],'sourceImageUrls':image_sources,'variantOptions':[{'name':x['name'],'values':x['values']} for x in p['options'] if x['name']!='Title']}
 with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:records=list(pool.map(convert,enumerate(curated)))
 output=ROOT/'data/merchant-catalog.json'
 output.write_text(json.dumps({'observedAt':observed,'collectionSource':URL,'merchant':'MUJI USA','pricingNote':'sourcePrice is original USD snapshot; price/stock are independent fictional course values','products':records},ensure_ascii=False,indent=2),encoding='utf-8')
 fields={'id':'id','name':'name','originalName':'original_name','category':'category','price':'price','stock':'stock','imageUrl':'image_url','tagline':'tagline','description':'description','brand':'brand','sourceType':'source_type','sourceUrl':'source_url','galleryJson':'gallery_json','specificationsJson':'specifications_json','sourcePrice':'source_price','sourceCurrency':'source_currency','sourceObservedAt':'source_observed_at','merchantName':'merchant_name'}
 lines=['-- AI-assisted factual merchant catalog snapshot; source links and original pricing are retained.']
 for record in records:lines.append('INSERT INTO product ('+','.join(fields.values())+') VALUES ('+','.join(sql(record[key]) for key in fields)+');')
 # Existing baskets stay purchasable. Do not delete products referenced by order snapshots.
 lines.append("UPDATE product SET active=FALSE WHERE source_type='DUMMYJSON' AND id NOT IN (SELECT product_id FROM cart_item);")
 if migration:migration.write_text('\n'.join(lines)+'\n',encoding='utf-8')
 else: print('Snapshot only: use --migration-version with a new, unused Flyway version to generate SQL.')
 print('Saved',len(records),'official merchant records and',sum(len(json.loads(r['galleryJson'])) for r in records),'local photos. Existing cart products are preserved by migration.')
if __name__=='__main__':main()
