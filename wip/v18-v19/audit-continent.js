export const meta = {
  name: 'audit-continent',
  description: 'Deep audit of the quick-pass catalog entries of one continent: two independent verifier lenses per batch, an adjudicator, a whole-continent critic, deterministic validation of every proposed fix',
  whenToUse: 'Auditing the quick-pass countries of a continent; args = {continent, compactFile, batches: [{id, file, codes}]}',
  phases: [
    { title: 'Verify', detail: 'facts lens and texts lens in parallel, per batch of 3 countries' },
    { title: 'Adjudicate', detail: 'one agent per batch confirms or drops every finding against sources' },
    { title: 'Critic', detail: 'whole-continent consistency review' },
  ],
}

const continent = args.continent
const batches = args.batches
const compactFile = args.compactFile

// Owner decisions that tests pin (CatalogPolicyTest) or that the owner kept on 2026-10-05. A fix that touches one of
// these never reaches the catalog: it becomes an owner decision in the PR text.
const PINNED = new Set([
  'AF.flagDescription', 'AF.flagColors', 'GQ.capital', 'GQ.quizCapital', 'TW.languages', 'TW.funFact', 'BF.languages',
  'HN.flagColors', 'HN.flagDescription', 'XK.funFact', 'PS.capital', 'PS.quizCapital', 'PS.currency', 'NR.capital', 'NR.quizCapital',
  'NL.capital', 'NL.quizCapital', 'CH.quizCapital', 'MY.capital', 'MY.quizCapital', 'LK.quizCapital', 'KZ.capital', 'CI.capital',
  'CI.quizCapital', 'BJ.quizCapital', 'SZ.quizCapital', 'BI.quizCapital', 'SD.capital', 'TO.capital', 'KI.capital',
  'FM.officialName', 'TV.currency', 'PW.currency', 'LC.currency', 'EC.currency', 'US.currency', 'PA.currency', 'BO.capital', 'BO.quizCapital',
])
const FIELDS = ['officialName', 'capital', 'quizCapital', 'population', 'areaSqKm', 'languages', 'currency', 'landmarks', 'funFact', 'driveSide', 'flagColors', 'flagDescription']
const LIST_FIELDS = ['languages', 'landmarks', 'flagColors']

const RULES = `You are auditing entries of an offline Android app that teaches world flags and geography to families (kids and adults). The app ships one Kotlin Country(...) entry per country. These entries came from a quick first research pass; your job is to find what is wrong or misleading in them before they stay in the app.

READ-ONLY JOB: do not edit, create or delete repository files, do not run Gradle. You may read files. Use WebSearch and WebFetch (load them with ToolSearch if they are not listed) and list the URLs you actually opened in "sources". Prefer government and encyclopedic sources (constitutions and flag laws, national statistics offices, UN, Britannica, the flag's own Wikipedia article and the sources it cites). A claim you could not verify is a "doubt", never a confident fix.

OWNER DECISIONS ARE NOT UP FOR CHANGE. If you believe one of these is wrong, report it with severity "owner" and no replacement; never as an error: Afghanistan shows the black-red-green tricolour; Equatorial Guinea's capital is "Malabo, Ciudad de la Paz" with quizCapital "Ciudad de la Paz"; Taiwan's languages are Mandarin only and its fun fact carries the agreed status sentence; Burkina Faso's language list is a best-effort list; Honduras shows the navy blue and its text is shade-neutral; Kosovo's fun fact carries the agreed status sentence; Palestine's capital is "Al Quds (Jerusalem)" with quizCapital "Al Quds" and its currency text starts "No currency of its own"; Nauru's capital is "Yaren (de facto)"; Israel has no entry and must not be added; Bolivia's quizCapital is Sucre; the Netherlands' quizCapital is Amsterdam; Malaysia's is Kuala Lumpur; Sri Lanka's is Sri Jayawardenepura Kotte; Cote d'Ivoire's is Yamoussoukro; Benin's is Porto-Novo; Eswatini's is Mbabane; Burundi's is Gitega.

FIELD RULES (a replacement you propose must follow them exactly)
- officialName: the state's full English name. capital: the city's Wikipedia title with accents; several cities sharing the role are written "City (role), City (role)" and quizCapital is the first, one city contained in the capital string. When you change capital you must also state the matching quizCapital in the same report. Capitals in transition: the law in force now, no dated wording.
- population: UN World Population Prospects 2024 revision, mid-2024 estimate, rounded to 3 significant figures (4 if the third digit would be 5, so no value is 3 digits ending in 5 followed by zeros). Kosovo: its statistics office. areaSqKm: total area (land plus inland water) per Wikipedia's list of countries by area.
- languages: official or national languages only, English spelling. currency: "Name (symbol or ISO code)"; fixed shared strings: "Euro (€)", "West African CFA Franc (CFA)", "Central African CFA Franc (FCFA)", "East Caribbean Dollar (EC$)", "US Dollar (US$)", "Australian Dollar (A$)".
- landmarks: exactly three well-known sights inside the country, "Name (Place)" with the place in brackets when needed; never ", " or " & " inside one landmark.
- funFact: one true sentence of at most 160 characters that will still be true in five years: no "currently", "recently", "fastest", "most visited", no year-stamped statistics; neutral tone.
- driveSide: "Right" or "Left". flagColors: main colours as plain words. The order is not audited: never report a reordering, only a colour that is missing or wrong (and "Light Blue" versus "Blue" only when the flag clearly uses the other shade).
- flagDescription (the flag story): 150 to 450 characters, one paragraph, explains WHY each visible feature is there (history, origin, what it commemorates, what the colours are officially said to mean, when and why adopted). Official or historical claims are stated plainly; traditional readings are hedged ("often said to", "traditionally linked to"); where no meaning is given, say so briefly instead of inventing one; never folklore as fact. Plain English for a 10-year-old, no flag jargon (hoist, fly, canton, field, charge, ensign, saltire, fimbriation, tricolour, bicolour), American spelling, no double quotes, no line breaks, nothing that goes out of date. Shared motifs (Pan-African, Pan-Arab, Pan-Slavic, Nordic cross, Union Jack, Southern Cross, Central American blue-white-blue, Gran Colombia yellow-blue-red) name the shared origin in one clause. Switzerland: "cantons", never "canton".
- Politics and religion: say what a flag or symbol is said to mean without endorsing it.`

const FINDING = {
  type: 'object',
  properties: {
    code: { type: 'string' },
    field: { type: 'string', description: 'officialName, capital, quizCapital, population, areaSqKm, languages, currency, landmarks, funFact, driveSide, flagColors, or flagDescription' },
    current: { type: 'string', description: 'The value in the catalog today, as text.' },
    proposed: { type: 'string', description: 'The exact replacement value; JSON text for lists and numbers; empty for a doubt or an owner-decision report.' },
    severity: { type: 'string', enum: ['error', 'improve', 'doubt', 'owner'], description: 'error = verifiably wrong; improve = accurate but breaks a rule or misleads; doubt = could not verify, no replacement; owner = touches an owner decision' },
    reason: { type: 'string' },
    sources: { type: 'array', items: { type: 'string' } },
    confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
  },
  required: ['code', 'field', 'current', 'proposed', 'severity', 'reason', 'sources', 'confidence'],
}
const FINDINGS = { type: 'object', properties: { findings: { type: 'array', items: FINDING }, checkedCodes: { type: 'array', items: { type: 'string' } }, notes: { type: 'string' } }, required: ['findings', 'checkedCodes', 'notes'] }
const FIX = {
  type: 'object',
  properties: {
    code: { type: 'string' }, field: { type: 'string' }, current: { type: 'string' }, value: { type: 'string', description: 'The exact replacement value; JSON text for lists and numbers.' },
    reason: { type: 'string' }, sources: { type: 'array', items: { type: 'string' } }, confidence: { type: 'string', enum: ['high', 'medium', 'low'] },
  },
  required: ['code', 'field', 'current', 'value', 'reason', 'sources', 'confidence'],
}
const ISSUE = { type: 'object', properties: { code: { type: 'string' }, field: { type: 'string' }, issue: { type: 'string' }, sources: { type: 'array', items: { type: 'string' } } }, required: ['code', 'field', 'issue', 'sources'] }
const VERDICT = {
  type: 'object',
  properties: {
    fixes: { type: 'array', items: FIX },
    ownerDecisions: { type: 'array', items: ISSUE },
    doubts: { type: 'array', items: ISSUE },
    rejected: { type: 'array', items: { type: 'object', properties: { code: { type: 'string' }, field: { type: 'string' }, why: { type: 'string' } }, required: ['code', 'field', 'why'] } },
    notes: { type: 'string' },
  },
  required: ['fixes', 'ownerDecisions', 'doubts', 'rejected', 'notes'],
}

const BATCH_INPUT = (batch) => `YOUR BATCH FILE: ${batch.file}
Read it first with the Read tool. It is JSON with ${batch.codes.length} countries (${batch.codes.join(', ')}). For each: "current" is exactly what the app ships today and is the thing you audit; "ownerRules" and "leftHandSeed" came from the seed; "priorDoubts" and "priorProvenance" are what the first research pass doubted and the sources it used (start with the doubts, they are the likeliest errors, but do not trust the earlier sources blindly).`

const FACTS_LENS = `LENS A, FACTS. Verify, for each country, in this order: capital and quizCapital; population (is it really the WPP 2024 mid-2024 figure, correctly rounded, not another vintage?); areaSqKm; official languages (constitution or language law); currency string; driveSide; the three landmarks (do they exist, are they inside this country, is the "Name (Place)" form right?); officialName; subregion (UN M49 geoscheme). Report only values that are wrong, likely wrong, or that break a field rule. Do not report style preferences or alternatives that are equally correct.`

const TEXTS_LENS = `LENS B, TEXTS. For each country: (1) flagDescription. Open the shipped flag image with the Read tool: /tmp/flagwork/out/<code in lowercase>.png (if missing, app/src/main/res/drawable-nodpi/flag_<code in lowercase>.webp in /home/user/Guess-countries). Check that every prominent feature is explained; that each claim's strength matches its basis (official or historical stated plainly, traditional hedged, unexplained said so); that years, counts and names are right against the flag law, the government or the flag's Wikipedia article; that a shared motif names its origin; that the text breaks none of the rules (length 150 to 450, jargon, American spelling, no double quotes or line breaks, nothing volatile). (2) funFact: true, at most 160 characters, still true in five years, neutral. (3) flagColors: match the image. Report only what is wrong, misleading, or breaks a rule. When you propose new text it must be complete and follow every rule above.`

phase('Verify')
const results = await pipeline(
  batches,
  async (batch) => {
    const label = batch.codes.join('+')
    const common = `${RULES}\n\n${BATCH_INPUT(batch)}\n\nTIME LIMIT: about 2 minutes per country (${2 * batch.codes.length} minutes for this batch) and at most about ${10 * batch.codes.length} tool calls. List every country you looked at in checkedCodes. Return findings as data; do not write prose outside the schema.`
    const [facts, texts] = await parallel([
      () => agent(`${common}\n\n${FACTS_LENS}`, { label: `facts:${label}`, phase: 'Verify', schema: FINDINGS }),
      () => agent(`${common}\n\n${TEXTS_LENS}`, { label: `texts:${label}`, phase: 'Verify', schema: FINDINGS }),
    ])
    return { facts, texts }
  },
  async (verified, batch) => {
    if (!verified || (!verified.facts && !verified.texts)) return null
    const label = batch.codes.join('+')
    const findings = [...(verified.facts ? verified.facts.findings : []), ...(verified.texts ? verified.texts.findings : [])]
    if (!findings.length) return { batch, verified, verdict: { fixes: [], ownerDecisions: [], doubts: [], rejected: [], notes: 'no findings from either lens' } }
    const verdict = await agent(
      `${RULES}\n\n${BATCH_INPUT(batch)}\n\nYou are the ADJUDICATOR for this batch. Two independent verifiers (facts lens and texts lens) produced the findings below. Decide each one. Keep a finding as a fix only when (a) you confirmed it yourself against an authoritative source you opened, or both verifiers' sources independently agree, (b) your replacement follows every field rule (count characters for texts and fun facts), and (c) it does not touch an owner decision. Findings of severity "doubt" go to doubts; "owner" ones and any fix that would change an owner decision go to ownerDecisions with the issue stated in one sentence; weak, stylistic or unconfirmed ones go to rejected with the reason. When a capital changes, return the matching quizCapital fix too. Fix "current" must be the catalog value as text. TIME LIMIT: about ${3 + batch.codes.length} minutes and at most about 14 tool calls.\n\nFINDINGS (JSON):\n${JSON.stringify(findings)}`,
      { label: `adjudicate:${label}`, phase: 'Adjudicate', schema: VERDICT }
    )
    return verdict ? { batch, verified, verdict } : null
  },
)

const done = results.filter(Boolean)
const missing = batches.filter((b, i) => !results[i]).map(b => b.id)
if (missing.length) log(`no result for batches: ${missing.join(', ')}`)

// Deterministic validation of every proposed fix against the dataset rules the tests also enforce.
const JARGON = /\b(hoist|ensign|saltire|fimbriation|tricolou?rs?|bicolou?rs?)\b/i
const VOLATILE = /\b(currently|recently|nowadays|fastest|most visited|as of)\b/i
const BRITISH_ONLY = /\b(colour\w*|centre\w*|grey|honour\w*|favour\w*|neighbour\w*|metre\w*|programme)\b/i
function tie(n) { const s = String(n).replace(/0+$/, ''); return s.length === 3 && s.endsWith('5') }
function problem(f) {
  if (!FIELDS.includes(f.field)) return { kind: 'invalid', why: 'unknown field ' + f.field }
  if (PINNED.has(`${f.code}.${f.field}`)) return { kind: 'owner', why: 'touches an owner decision' }
  let v = f.value
  if (LIST_FIELDS.includes(f.field)) {
    try { v = JSON.parse(v) } catch (e) { return { kind: 'invalid', why: 'list is not JSON' } }
    if (!Array.isArray(v) || !v.length || !v.every(x => typeof x === 'string' && x.trim())) return { kind: 'invalid', why: 'list must hold non-empty strings' }
  }
  const strings = Array.isArray(v) ? v : (typeof v === 'string' ? [v] : [])
  if (strings.some(s => /["\n\r]/.test(s))) return { kind: 'invalid', why: 'double quote or line break' }
  if (f.field === 'population') {
    const n = Number(String(f.value).replace(/[,\s]/g, ''))
    if (!Number.isInteger(n) || n <= 0) return { kind: 'invalid', why: 'population must be a positive integer' }
    if (tie(n)) return { kind: 'invalid', why: 'population is on a 3 significant figure rounding tie' }
  }
  if (f.field === 'areaSqKm') { const n = Number(String(f.value).replace(/,/g, '')); if (!(n > 0)) return { kind: 'invalid', why: 'area must be positive' } }
  if (f.field === 'driveSide' && !['Right', 'Left'].includes(f.value)) return { kind: 'invalid', why: 'driveSide must be Right or Left' }
  if (f.field === 'flagColors' && f.current) {
    const words = (t) => (String(t).match(/[A-Za-z][A-Za-z ]*[A-Za-z]|[A-Za-z]/g) || []).map(x => x.trim().toLowerCase()).sort().join('|')
    if (words(f.current) === words(f.value)) return { kind: 'invalid', why: 'colour order only' }
  }
  if (f.field === 'landmarks') {
    if (v.length !== 3) return { kind: 'invalid', why: 'exactly three landmarks' }
    if (v.some(x => x.includes(', ') || x.includes(' & '))) return { kind: 'invalid', why: 'landmark with ", " or " & "' }
  }
  if (f.field === 'flagDescription') {
    if (f.value.length < 150 || f.value.length > 450) return { kind: 'invalid', why: `flag text is ${f.value.length} characters, needs 150 to 450` }
    if (JARGON.test(f.value) || (f.code !== 'CH' && /\bcanton\b/i.test(f.value))) return { kind: 'invalid', why: 'flag jargon' }
    if (BRITISH_ONLY.test(f.value)) return { kind: 'invalid', why: 'British spelling' }
    if (VOLATILE.test(f.value)) return { kind: 'invalid', why: 'volatile wording' }
  }
  if (f.field === 'funFact') {
    if (f.value.length > 160) return { kind: 'invalid', why: `fun fact is ${f.value.length} characters, needs at most 160` }
    if (VOLATILE.test(f.value)) return { kind: 'invalid', why: 'volatile wording' }
    if (BRITISH_ONLY.test(f.value)) return { kind: 'invalid', why: 'British spelling' }
  }
  if (f.current !== undefined && String(f.current).trim() === String(f.value).trim()) return { kind: 'invalid', why: 'no change' }
  return null
}

const fixes = [], ownerDecisions = [], doubts = [], rejected = [], notes = []
function take(source, list, batchId) {
  for (const f of list) {
    const p = problem(f)
    if (!p) fixes.push({ ...f, from: source })
    else if (p.kind === 'owner') ownerDecisions.push({ code: f.code, field: f.field, issue: `${f.reason} (proposed: ${String(f.value).slice(0, 200)})`, sources: f.sources || [], from: source })
    else rejected.push({ code: f.code, field: f.field, why: p.why, from: source })
  }
}
for (const r of done) {
  take(r.batch.id, r.verdict.fixes, r.batch.id)
  for (const o of r.verdict.ownerDecisions) ownerDecisions.push({ ...o, from: r.batch.id })
  for (const d of r.verdict.doubts) doubts.push({ ...d, from: r.batch.id })
  for (const x of r.verdict.rejected) rejected.push({ ...x, from: r.batch.id })
  if (r.verdict.notes) notes.push(`${r.batch.id}: ${r.verdict.notes}`)
}
log(`${fixes.length} fixes, ${ownerDecisions.length} owner decisions, ${doubts.length} doubts, ${rejected.length} rejected after adjudication and validation`)

phase('Critic')
const critic = await agent(
  `${RULES}\n\nYou are the WHOLE-CONTINENT CRITIC for ${continent}. The file ${compactFile} holds every ${continent} entry in the catalog today (field "quickPass" marks the ones being audited now; the others were audited before and are the reference for consistency). It is JSON; read it in parts with the Read tool (offset and limit). The batch-level fixes already decided are below; assume they will be applied.\n\nCheck the entries TOGETHER for what a single-batch view cannot see: the same shared currency written two ways; a shared flag motif explained differently or not at all across countries; some flag texts hedging and others stating the same kind of claim as fact; two countries with the same quizCapital; capitals or names that break the rules; a fun fact repeating another country's; inconsistent subregions; facts that look wrong in comparison with neighbours (areas, populations, languages). For every problem give the code, the field and the exact replacement value, following the field rules; do not touch owner decisions. TIME LIMIT: about 8 minutes and at most about 20 tool calls; verify only the riskiest claims.\n\nFIXES ALREADY DECIDED (JSON):\n${JSON.stringify(fixes.map(f => ({ code: f.code, field: f.field, value: f.value })))}`,
  { label: 'critic', phase: 'Critic', schema: { type: 'object', properties: { fixes: { type: 'array', items: FIX }, notes: { type: 'array', items: { type: 'string' } } }, required: ['fixes', 'notes'] } }
)
const criticFixes = []
if (critic) {
  for (const f of critic.fixes) {
    const p = problem(f)
    if (!p) { fixes.push({ ...f, from: 'critic' }); criticFixes.push(f) }
    else if (p.kind === 'owner') ownerDecisions.push({ code: f.code, field: f.field, issue: `${f.reason} (proposed: ${String(f.value).slice(0, 200)})`, sources: f.sources || [], from: 'critic' })
    else rejected.push({ code: f.code, field: f.field, why: p.why, from: 'critic' })
  }
  for (const n of critic.notes) notes.push(`critic: ${n}`)
}

// A field fixed twice (batch and critic): the later one wins; report it so it is looked at.
const last = {}
const duplicates = []
for (const f of fixes) {
  const key = `${f.code}.${f.field}`
  if (last[key]) duplicates.push(key)
  last[key] = f
}
const finalFixes = Object.values(last)
log(`critic added ${criticFixes.length} fixes; ${finalFixes.length} fixes in total${duplicates.length ? '; fixed twice: ' + duplicates.join(', ') : ''}`)

return {
  continent,
  batchesRun: done.length,
  batchesMissing: missing,
  fixes: finalFixes,
  ownerDecisions,
  doubts,
  rejected,
  notes,
  duplicates,
  perBatch: done.map(r => ({
    id: r.batch.id,
    codes: r.batch.codes,
    factsFindings: r.verified.facts ? r.verified.facts.findings.length : null,
    textsFindings: r.verified.texts ? r.verified.texts.findings.length : null,
    fixes: r.verdict.fixes.length,
  })),
}
