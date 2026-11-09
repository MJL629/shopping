// AI-assisted regression coverage for editable date filters and inclusive one-year bounds.
import test from 'node:test';import assert from 'node:assert/strict';
import {validReportRange} from '../src/lib/report-range.js';
test('valid report ranges include the same date and up to 366 calendar dates',()=>{
 assert.equal(validReportRange('2026-11-09','2026-11-09'),true);
 assert.equal(validReportRange('2026-01-01','2027-01-01'),true);
});
test('invalid calendar dates, reversed and excessive ranges cannot hide report controls',()=>{
 for(const range of [['','2026-11-09'],['2026-11-09','2026-11-01'],['2026-02-30','2026-03-01'],['2026-01-01','2027-01-02']])assert.equal(validReportRange(...range),false);
});
