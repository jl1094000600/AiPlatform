import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { setLocale, t } from '../i18n/index.js'

const source = readFileSync(new URL('../views/ModelList.vue', import.meta.url), 'utf8')

assert.ok(source.includes('value="CHAT"'))
assert.ok(source.includes('value="VISION"'))
assert.ok(source.includes('value="ASR"'))
assert.ok(source.includes('v-model="form.defaultForCapability"'))
assert.ok(source.includes(':active-value="1"'))

setLocale('zh')
assert.equal(t('model.capabilityVision'), '视觉 VISION')
setLocale('en')
assert.equal(t('model.capabilityAsr'), 'Speech Recognition (ASR)')
setLocale('zh')
