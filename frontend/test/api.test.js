// AI-assisted API contract regression: authentication recovery never repeats a write.
import test from 'node:test';
import assert from 'node:assert/strict';
import {request,setAuthenticationRequired,session} from '../src/lib/api.js';
test('expired authentication prompts recovery once and does not replay checkout',async()=>{
 const original=globalThis.fetch;let prompted=0,writes=0;
 setAuthenticationRequired(()=>prompted++);
 globalThis.fetch=async(url)=>{if(url.endsWith('/auth/session'))return Response.json({user:null,csrfToken:'test-token'});writes++;return Response.json({detail:'请先登录'},{status:401});};
 try { await session(); await assert.rejects(request('/orders',{method:'POST',body:{}}),/请先登录/);assert.equal(prompted,1);assert.equal(writes,1); }
 finally{globalThis.fetch=original;setAuthenticationRequired(()=>{});}
});
test('incorrect login and forbidden admin access do not trigger a login loop',async()=>{
 const original=globalThis.fetch;let prompted=0;setAuthenticationRequired(()=>prompted++);
 globalThis.fetch=async(url)=>Response.json({detail:'拒绝'},{status:url.endsWith('/auth/login')?401:403});
 try{await assert.rejects(request('/auth/login',{method:'POST',body:{}}));await assert.rejects(request('/admin/stats'));assert.equal(prompted,0);}
 finally{globalThis.fetch=original;setAuthenticationRequired(()=>{});}
});
test('file upload preserves FormData and lets the browser choose its multipart boundary',async()=>{
 const original=globalThis.fetch;let sent;const form=new FormData();form.append('file',new Blob(['image']), 'cover.png');
 globalThis.fetch=async(url,options)=>{sent=options;return Response.json({imageUrl:'/uploads/test.png'});};
 try{await request('/admin/images',{method:'POST',body:form});assert.equal(sent.body,form);assert.equal(sent.headers['Content-Type'],undefined);assert.ok(sent.headers['X-XSRF-TOKEN']);}
 finally{globalThis.fetch=original;}
});
