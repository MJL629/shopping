// AI-assisted regression tests for shopping continuation and shareable filters.
import test from 'node:test';
import assert from 'node:assert/strict';
import {parseCatalogState,catalogHash} from '../src/lib/catalog-state.js';
test('category, keywords, sorting and page survive a URL round trip',()=>{
 const state={keyword:'钢笔 & 纸',category:'书写用品',sort:'price_desc',page:3};
 assert.deepEqual(parseCatalogState(catalogHash(state)),{...state,scrollY:0});
});
test('untrusted URL fields cannot restore unknown sorting or invalid pagination',()=>{
 const state=parseCatalogState('#/?page=-7&sort=price%3BDROP+TABLE&q='+('x'.repeat(110)));
 assert.equal(state.page,1); assert.equal(state.sort,'featured'); assert.equal(state.keyword.length,100);
 assert.equal(parseCatalogState('#/?page=1.5').page,1);
 assert.equal(catalogHash({keyword:'',category:'',sort:'featured',page:1}),'#/');
});
