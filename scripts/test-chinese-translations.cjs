// Dictionary regressions are checked without browser, network or runtime data access.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const repo = path.resolve(__dirname, '..');
const ts = require(require.resolve('typescript', { paths: [path.join(repo, 'frontend')] }));
const cache = new Map();
function load(file) {
  if (!path.extname(file)) file += '.ts';
  file = path.resolve(file);
  if (cache.has(file)) return cache.get(file).exports;
  const module = { exports: {} };
  cache.set(file, module);
  const compiled = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText;
  vm.runInNewContext(`(function(require,module,exports){${compiled}})`)(
    name => load(path.resolve(path.dirname(file), name)), module, module.exports
  );
  return module.exports;
}
const en = load(path.join(repo, 'frontend/src/i18n/translations/en.ts')).default;
const zh = load(path.join(repo, 'frontend/src/i18n/translations/zh_cn.ts')).default;
const variables = text => [...new Set([...text.matchAll(/\{\{\s*([^}]+?)\s*\}\}/g)].map(m => m[1]))].sort();
const unchanged = new Set(['change_from_to', 'App Store', 'Google Play']);
for (const [key, value] of Object.entries(en)) {
  assert.ok(typeof zh[key] === 'string' && zh[key].trim(), `Missing Chinese translation: ${key}`);
  assert.deepEqual(variables(zh[key]), variables(value), `Interpolation mismatch: ${key}`);
  if (/[a-zA-Z]{3}/.test(value) && !unchanged.has(key)) {
    assert.notEqual(zh[key], value, `Untranslated English: ${key}`);
  }
}
for (const label of ['Powered by Intelloop', 'Assigned users', 'Theme Settings', 'Remove Checklist', 'Edit profile']) {
  assert.ok(/[\u3400-\u9fff]/.test(zh[label]), `Missing legacy UI label: ${label}`);
}
assert.ok(zh['Powered by Intelloop'].includes('Intelloop'), 'Keep author attribution');
const currencySeed = fs.readFileSync(path.join(repo, 'api/src/main/resources/db/mysql/reference-data.sql'), 'utf8').split(';')[0];
for (const match of currencySeed.matchAll(/\(\d+,'([^']+)','[^']+'\)/g)) {
  assert.ok(/[\u3400-\u9fff]/.test(zh[match[1]]), `Missing currency label: ${match[1]}`);
}
console.log(JSON.stringify({ result: 'PASS', englishKeys: Object.keys(en).length, chineseKeys: Object.keys(zh).length, interpolationChecked: true }));
