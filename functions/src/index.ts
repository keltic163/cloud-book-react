import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as functionsV1 from "firebase-functions/v1";
import { defineSecret } from "firebase-functions/params";
import { BetaAnalyticsDataClient } from "@google-analytics/data";
import * as admin from "firebase-admin";

admin.initializeApp();

const ADMIN_EMAIL_ALLOWLIST = new Set<string>([
  's1594622@gmail.com',
  'chian0163@gmail.com',
]);

// 1. 定義 Secret
const geminiApiKey = defineSecret("GEMINI_API_KEY");
// Optional developer test code: when the frontend sends this exact code as the API Key,
// the function will substitute the server-side GEMINI_API_KEY for that request.
const devKeyCode = defineSecret("DEV_KEY_CODE");
const ga4PropertyId = defineSecret("GA4_PROPERTY_ID");
const ga4ServiceAccount = defineSecret("GA4_SERVICE_ACCOUNT_JSON");

// ??修改：�?義�??��??�設?��? (?��?�?constants.ts 保�?一??
const DEFAULT_EXPENSE_CATEGORIES = [
  '餐飲',
  '交通',
  '日常',
  '居家',
  '娛樂',
  '社交',
  '教育',
  '其他'
];

const DEFAULT_INCOME_CATEGORIES = [
  '薪資',
  '獎金',
  '投資',
  '兼職',
  '副業收入',
  '點數折抵',
  '其他'
];

type TransactionStatsInput = {
  amount: number;
  type: 'INCOME' | 'EXPENSE';
  category: string;
  rewards: number;
  date: string;
  creatorUid: string;
  targetUserUid?: string | null;
  deleted: boolean;
};

type MonthlyStatsDoc = {
  totalIncome?: number;
  totalExpense?: number;
  totalRewards?: number;
  transactionCount?: number;
  categoryIncome?: Record<string, number>;
  categoryExpense?: Record<string, number>;
  memberIncome?: Record<string, number>;
  memberExpense?: Record<string, number>;
  updatedAt?: any;
};

const tokenizeTransaction = (data: Record<string, any>) => {
  const source = [
    data.description,
    data.category,
    data.amount,
    data.rewards,
  ]
    .filter((value) => value !== undefined && value !== null)
    .join(' ')
    .toLowerCase();
  const tokens = new Set<string>();
  const normalized = source.normalize('NFKC');
  normalized
    .split(/[\s,，.。:：;；/\\|()[\]{}'"!?！？+-]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach((part) => {
      tokens.add(part);
      if (part.length >= 2) {
        for (let size = 2; size <= Math.min(4, part.length); size += 1) {
          for (let index = 0; index <= part.length - size; index += 1) {
            tokens.add(part.slice(index, index + size));
          }
        }
      }
    });
  return Array.from(tokens).slice(0, 80);
};

const parseTransactionStatsInput = (data: Record<string, any> | undefined): TransactionStatsInput | null => {
  if (!data) return null;
  const amount = Number(data.amount);
  if (!Number.isFinite(amount)) return null;
  const date = typeof data.date === 'string' ? data.date : '';
  if (!/^\d{4}-\d{2}-\d{2}/.test(date)) return null;
  const type = data.type === 'INCOME' ? 'INCOME' : 'EXPENSE';
  return {
    amount,
    type,
    category: typeof data.category === 'string' ? data.category : '',
    rewards: Number(data.rewards) || 0,
    date: date.slice(0, 10),
    creatorUid: typeof data.creatorUid === 'string' ? data.creatorUid : '',
    targetUserUid: typeof data.targetUserUid === 'string' ? data.targetUserUid : null,
    deleted: data.deleted === true
  };
};

const monthKeyFromDate = (date: string) => date.slice(0, 7);
const yearFromDate = (date: string) => Number(date.slice(0, 4)) || null;
const statsMemberId = (tx: TransactionStatsInput) => tx.targetUserUid || tx.creatorUid || 'unknown';

const addRecordValue = (record: Record<string, number>, key: string, delta: number) => {
  if (!key || delta === 0) return;
  const next = (record[key] || 0) + delta;
  if (Math.abs(next) < 0.000001) {
    delete record[key];
  } else {
    record[key] = next;
  }
};

const applyMonthlyDelta = (
  stats: MonthlyStatsDoc,
  tx: TransactionStatsInput,
  direction: 1 | -1
) => {
  const categoryIncome = { ...(stats.categoryIncome || {}) };
  const categoryExpense = { ...(stats.categoryExpense || {}) };
  const memberIncome = { ...(stats.memberIncome || {}) };
  const memberExpense = { ...(stats.memberExpense || {}) };
  const amountDelta = tx.amount * direction;
  const rewardsDelta = tx.rewards * direction;
  const memberId = statsMemberId(tx);

  if (tx.type === 'INCOME') {
    stats.totalIncome = (stats.totalIncome || 0) + amountDelta;
    addRecordValue(categoryIncome, tx.category, amountDelta);
    addRecordValue(memberIncome, memberId, amountDelta + rewardsDelta);
  } else {
    stats.totalExpense = (stats.totalExpense || 0) + amountDelta;
    addRecordValue(categoryExpense, tx.category, amountDelta);
    addRecordValue(memberExpense, memberId, amountDelta);
  }
  if (tx.rewards) {
    stats.totalIncome = (stats.totalIncome || 0) + rewardsDelta;
    stats.totalRewards = (stats.totalRewards || 0) + rewardsDelta;
  }
  stats.transactionCount = (stats.transactionCount || 0) + direction;
  stats.categoryIncome = categoryIncome;
  stats.categoryExpense = categoryExpense;
  stats.memberIncome = memberIncome;
  stats.memberExpense = memberExpense;
};

const normalizeTransactionPatch = (data: Record<string, any>) => {
  const parsed = parseTransactionStatsInput(data);
  if (!parsed) return {};
  return {
    monthKey: monthKeyFromDate(parsed.date),
    year: yearFromDate(parsed.date),
    searchTokens: tokenizeTransaction(data)
  };
};

const patchesAreEqual = (current: Record<string, any>, patch: Record<string, any>) => {
  return Object.entries(patch).every(([key, value]) => {
    if (Array.isArray(value)) {
      const existing = Array.isArray(current[key]) ? current[key] : [];
      return existing.length === value.length && existing.every((item, index) => item === value[index]);
    }
    return current[key] === value;
  });
};
const GEMINI_MODELS_ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta/models';
const GEMINI_MODEL_PREFERENCE = [
  'gemini-2.5-flash',
  'gemini-2.5-flash-lite',
  'gemini-2.5-pro',
  'gemini-1.5-flash',
  'gemini-1.5-pro'
];

interface SmartInputRequest {
  text: string;
  categories?: string[];
}

const isAdminEmail = (email?: string | null) => {
  if (!email) return false;
  return ADMIN_EMAIL_ALLOWLIST.has(email.toLowerCase());
};

const ensureAdminAccess = (request: any) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Please sign in');
  }

  const email = (request.auth.token?.email as string | undefined) || null;
  const isAdminClaim = request.auth.token?.admin === true;
  if (!isAdminClaim && !isAdminEmail(email)) {
    throw new HttpsError('permission-denied', 'Admin access required');
  }

  return {
    uid: request.auth.uid,
    email,
  };
};

const listGeminiModels = async (apiKey: string) => {
  const url = `${GEMINI_MODELS_ENDPOINT}?key=${encodeURIComponent(apiKey)}`;
  const response = await fetch(url);
  if (!response.ok) {
    const errorText = await response.text().catch(() => '');
    throw new HttpsError(
      'invalid-argument',
      `Gemini model list failed: ${response.status} ${errorText}`
    );
  }
  const data = (await response.json()) as { models?: Array<{ name?: string; supportedGenerationMethods?: string[] }> };
  const models = (data.models || [])
    .filter((model) => (model.supportedGenerationMethods || []).includes('generateContent'))
    .map((model) => (model.name || '').replace(/^models\//, ''))
    .filter(Boolean);
  const sorted = models.sort((a, b) => {
    const aIdx = GEMINI_MODEL_PREFERENCE.indexOf(a);
    const bIdx = GEMINI_MODEL_PREFERENCE.indexOf(b);
    if (aIdx === -1 && bIdx === -1) return a.localeCompare(b);
    if (aIdx === -1) return 1;
    if (bIdx === -1) return -1;
    return aIdx - bIdx;
  });
  return sorted;
};

const pickPreferredModel = (models: string[]) => {
  if (!models.length) return null;
  for (const preferred of GEMINI_MODEL_PREFERENCE) {
    if (models.includes(preferred)) return preferred;
  }
  return models[0];
};

const parseGa4Credentials = () => {
  const raw = ga4ServiceAccount.value();
  try {
    const parsed = JSON.parse(raw) as { client_email?: string; private_key?: string };
    if (!parsed.client_email || !parsed.private_key) {
      throw new Error('Missing client_email/private_key');
    }
    return {
      client_email: parsed.client_email,
      private_key: parsed.private_key.replace(/\\n/g, '\n'),
    };
  } catch (error: any) {
    console.error('GA4 credentials parse failed:', String(error));
    throw new HttpsError('internal', 'GA4 credentials invalid');
  }
};

// 2. Parse transaction
export const parseTransactionHandler = async (request: any) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Please sign in');
  }

  const { text, categories } = request.data as SmartInputRequest;
  const providedKey = (request.data as any).apiKey as string | undefined;

  const defaultAllCategories = [...DEFAULT_EXPENSE_CATEGORIES, ...DEFAULT_INCOME_CATEGORIES];
  const availableCategories = categories && categories.length > 0
    ? categories
    : defaultAllCategories;

  let apiKeyToUse: string;
  try {
    if (providedKey) {
      if (providedKey === devKeyCode.value()) {
        apiKeyToUse = geminiApiKey.value();
      } else {
        apiKeyToUse = providedKey;
      }
    } else {
      apiKeyToUse = geminiApiKey.value();
    }
  } catch (e: any) {
    console.error('Key resolution failed:', String(e));
    throw new HttpsError('internal', 'Key resolution failed');
  }

  const { GoogleGenAI } = await import("@google/genai");
  const ai = new GoogleGenAI({ apiKey: apiKeyToUse });
  const today = getTaipeiDateKey(new Date());

  try {
    const availableModels = await listGeminiModels(apiKeyToUse);
    const model = pickPreferredModel(availableModels);
    if (!model) {
      throw new HttpsError('failed-precondition', 'Failed precondition');
    }

    const response = await ai.models.generateContent({
      model,
      contents: `
        Analyze this financial input: "${text}".
        Context: Today is ${today}.
        Requirements:
        1. Amount: Extract number.
        2. Type: 'EXPENSE' or 'INCOME'.
        3. Category: Select strictly from: [${availableCategories.join(', ')}]. If unsure, use '其他'.
        4. Description: Short summary in Traditional Chinese (NO numbers).
        5. Rewards: Extract points/cashback value.
        6. Date: YYYY-MM-DD format if mentioned, else null.
      `,
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: "OBJECT",
          properties: {
            amount: { type: "NUMBER" },
            type: { type: "STRING", enum: ["INCOME", "EXPENSE"] },
            category: { type: "STRING", enum: availableCategories },
            description: { type: "STRING" },
            rewards: { type: "NUMBER" },
            date: { type: "STRING" }
          },
          required: ["amount", "type", "category", "description"],
        },
      },
    });

    const resultText = response.text;
    if (!resultText) throw new Error('No response from AI');

    return JSON.parse(resultText);

  } catch (error: any) {
    console.error('Gemini Backend Error:', error);
    throw new HttpsError('internal', 'AI parse failed');
  }
};
export const parseTransaction = onCall(
  { secrets: [geminiApiKey, devKeyCode] },
  parseTransactionHandler
);

export const validateKeyHandler = async (request: any) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Please sign in');
  }

  const providedKey = (request.data as any).apiKey as string | undefined;
  if (!providedKey) {
    return { valid: false, models: [] };
  }

  let keyToUse: string;
  try {
    if (providedKey === devKeyCode.value()) {
      keyToUse = geminiApiKey.value();
    } else {
      keyToUse = providedKey;
    }
  } catch (e: any) {
    console.error('Key resolution failed:', String(e));
    throw new HttpsError('internal', 'Key resolution failed');
  }

  try {
    const available = await listGeminiModels(keyToUse);
    const valid = available.length > 0;
    return { valid, models: available };
  } catch (e: any) {
    console.error('validateKey failed:', e);
    return { valid: false, models: [] };
  }
};

export const validateKey = onCall(
  { secrets: [geminiApiKey, devKeyCode] },
  validateKeyHandler
);

export const adminCheckHandler = async (request: any) => {
  const { email } = ensureAdminAccess(request);

  return { ok: true, email };
};

export const adminCheck = onCall(adminCheckHandler);

export const getVipStatusHandler = async (request: any) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Please sign in');
  }

  const email = (request.auth.token?.email as string | undefined) || null;
  const isAdminVip = isAdminEmail(email);
  const isPurchaseVip = request.auth.token?.vip === true;

  return {
    isVip: isAdminVip || isPurchaseVip,
    sources: {
      admin: isAdminVip,
      purchase: isPurchaseVip
    }
  };
};

export const getVipStatus = onCall(getVipStatusHandler);

export const adminGetAnnouncementHandler = async (request: any) => {
  ensureAdminAccess(request);

  const payload = request.data as { platform?: string } | undefined;
  const platform = payload?.platform === 'android' ? 'android' : 'web';
  const docId = platform === 'android' ? 'announcement_android' : 'announcement_web';

  const snap = await admin.firestore().doc(`app_settings/${docId}`).get();
  if (!snap.exists) {
    return { exists: false };
  }

  const data = snap.data() || {};
  return {
    exists: true,
    text: data.text ?? '',
    isEnabled: Boolean(data.isEnabled),
    type: data.type ?? 'info',
    startAt: data.startAt?.toMillis ? data.startAt.toMillis() : null,
    endAt: data.endAt?.toMillis ? data.endAt.toMillis() : null
  };
};

export const adminGetAnnouncement = onCall(adminGetAnnouncementHandler);

export const adminSetAnnouncementHandler = async (request: any) => {
  ensureAdminAccess(request);

  const payload = request.data as {
    text?: string;
    isEnabled?: boolean;
    type?: 'info' | 'warning' | 'error';
    startAt?: string | number | null;
    endAt?: string | number | null;
    platform?: string;
  };

  const text = typeof payload.text === 'string' ? payload.text.trim() : '';
  const isEnabled = Boolean(payload.isEnabled);
  const type = payload.type === 'warning' || payload.type === 'error' ? payload.type : 'info';
  const platform = payload.platform === 'android' ? 'android' : 'web';
  const docId = platform === 'android' ? 'announcement_android' : 'announcement_web';

  const toMillis = (value: string | number | null | undefined) => {
    if (value === null || value === undefined) return null;
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    if (typeof value === 'string') {
      const parsed = Date.parse(value);
      return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
  };

  const startMs = toMillis(payload.startAt);
  const endMs = toMillis(payload.endAt);

  if (!text) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }
  if (!startMs || !endMs) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }
  if (startMs >= endMs) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }

  await admin.firestore().doc(`app_settings/${docId}`).set({
    text,
    isEnabled,
    type,
    startAt: admin.firestore.Timestamp.fromMillis(startMs),
    endAt: admin.firestore.Timestamp.fromMillis(endMs),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });

  return { ok: true };
};

export const adminSetAnnouncement = onCall(adminSetAnnouncementHandler);

export const adminGetUsageSummaryHandler = async (request: any) => {
  ensureAdminAccess(request);

  const payload = request.data as { startDate?: string; endDate?: string } | undefined;
  const startDate = payload?.startDate;
  const endDate = payload?.endDate;
  const datePattern = /^\d{4}-\d{2}-\d{2}$/;
  if (!startDate || !endDate || !datePattern.test(startDate) || !datePattern.test(endDate)) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }

  const client = new BetaAnalyticsDataClient({
    credentials: parseGa4Credentials(),
  });
  const propertyId = ga4PropertyId.value();

  const [report] = await client.runReport({
    property: `properties/${propertyId}`,
    dateRanges: [{ startDate, endDate }],
    metrics: [
      { name: 'activeUsers' },
      { name: 'newUsers' },
      { name: 'sessions' },
      { name: 'eventCount' },
    ],
  });

  const totals = report.totals?.[0]?.metricValues || [];
  const getMetric = (index: number) => Number(totals[index]?.value ?? 0);

  return {
    startDate,
    endDate,
    metrics: {
      activeUsers: getMetric(0),
      newUsers: getMetric(1),
      sessions: getMetric(2),
      eventCount: getMetric(3),
    },
  };
};

export const adminGetUsageSummary = onCall(
  { secrets: [ga4PropertyId, ga4ServiceAccount] },
  adminGetUsageSummaryHandler
);

export const adminFindUserHandler = async (request: any) => {
  ensureAdminAccess(request);

  const payload = request.data as { identifier?: string } | undefined;
  const identifier = payload?.identifier?.trim();
  if (!identifier) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }

  const db = admin.firestore();
  let snap: FirebaseFirestore.DocumentSnapshot | null = null;

  if (identifier.includes('@')) {
    const query = await db.collection('users').where('email', '==', identifier).limit(1).get();
    snap = query.empty ? null : query.docs[0];
  } else {
    const doc = await db.collection('users').doc(identifier).get();
    snap = doc.exists ? doc : null;
  }

  if (!snap || !snap.exists) {
    return { exists: false };
  }

  const data = snap.data() || {};
  return {
    exists: true,
    uid: snap.id,
    email: data.email ?? null,
    displayName: data.displayName ?? null,
    isAdFree: Boolean(data.isAdFree),
    adFreeUpdatedAt: data.adFreeUpdatedAt?.toMillis ? data.adFreeUpdatedAt.toMillis() : null,
    adFreeUpdatedBy: data.adFreeUpdatedBy ?? null,
  };
};

export const adminFindUser = onCall(adminFindUserHandler);

export const adminSetAdFreeHandler = async (request: any) => {
  const { email, uid: adminUid } = ensureAdminAccess(request);

  const payload = request.data as { uid?: string; isAdFree?: boolean } | undefined;
  const targetUid = payload?.uid?.trim();
  if (!targetUid) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }
  if (typeof payload?.isAdFree !== 'boolean') {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }

  const userRef = admin.firestore().collection('users').doc(targetUid);
  const userSnap = await userRef.get();
  if (!userSnap.exists) {
    throw new HttpsError('not-found', 'Not found');
  }

  await userRef.set({
    isAdFree: payload.isAdFree,
    adFreeUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
    adFreeUpdatedBy: email ?? adminUid,
  }, { merge: true });

  return { ok: true };
};

export const adminSetAdFree = onCall(adminSetAdFreeHandler);

export const onUserCreate = functionsV1.auth.user().onCreate(async (user) => {
  const db = admin.firestore();
  await db.collection('users').doc(user.uid).set({
    displayName: user.displayName ?? null,
    email: user.email ?? null,
    photoURL: user.photoURL ?? null,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    isAdFree: false,
    adFreeUpdatedAt: null,
    adFreeUpdatedBy: null,
    ledgers: []
  }, { merge: true });
});

export const onLedgerTransactionWrite = functionsV1.firestore
  .document('ledgers/{ledgerId}/transactions/{transactionId}')
  .onWrite(async (change, context) => {
    const ledgerId = context.params.ledgerId as string;
    const beforeData = change.before.exists ? change.before.data() : undefined;
    const afterData = change.after.exists ? change.after.data() : undefined;
    const beforeTx = parseTransactionStatsInput(beforeData);
    const afterTx = parseTransactionStatsInput(afterData);
    const db = admin.firestore();

    if (afterData && !afterData.deleted) {
      const patch = normalizeTransactionPatch(afterData);
      if (Object.keys(patch).length > 0 && !patchesAreEqual(afterData, patch)) {
        await change.after.ref.set(patch, { merge: true });
      }
    }

    const activeBefore = beforeTx && !beforeTx.deleted ? beforeTx : null;
    const activeAfter = afterTx && !afterTx.deleted ? afterTx : null;
    const affectedMonths = new Set<string>();
    if (activeBefore) affectedMonths.add(monthKeyFromDate(activeBefore.date));
    if (activeAfter) affectedMonths.add(monthKeyFromDate(activeAfter.date));
    if (affectedMonths.size === 0) return null;

    await db.runTransaction(async (tx) => {
      const refs = Array.from(affectedMonths).map((monthKey) => ({
        monthKey,
        ref: db.doc(`ledgers/${ledgerId}/monthlyStats/${monthKey}`)
      }));
      const snaps = await Promise.all(refs.map((entry) => tx.get(entry.ref)));
      refs.forEach((entry, index) => {
        const stats: MonthlyStatsDoc = snaps[index].exists ? (snaps[index].data() || {}) : {};
        if (activeBefore && monthKeyFromDate(activeBefore.date) === entry.monthKey) {
          applyMonthlyDelta(stats, activeBefore, -1);
        }
        if (activeAfter && monthKeyFromDate(activeAfter.date) === entry.monthKey) {
          applyMonthlyDelta(stats, activeAfter, 1);
        }
        tx.set(entry.ref, {
          ...stats,
          monthKey: entry.monthKey,
          year: Number(entry.monthKey.slice(0, 4)),
          updatedAt: admin.firestore.FieldValue.serverTimestamp()
        }, { merge: true });
      });
    });

    return null;
  });

export const adminRebuildLedgerStatsHandler = async (request: any) => {
  ensureAdminAccess(request);
  const ledgerId = (request.data as any)?.ledgerId as string | undefined;
  if (!ledgerId) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }

  const db = admin.firestore();
  const txCollection = db.collection(`ledgers/${ledgerId}/transactions`);
  const monthly = new Map<string, MonthlyStatsDoc>();
  let scanned = 0;
  let updatedTransactions = 0;
  let lastDoc: FirebaseFirestore.QueryDocumentSnapshot | null = null;

  while (true) {
    let q = txCollection.orderBy(admin.firestore.FieldPath.documentId()).limit(400);
    if (lastDoc) q = q.startAfter(lastDoc);
    const snap = await q.get();
    if (snap.empty) break;

    const batch = db.batch();
    snap.docs.forEach((docSnap) => {
      scanned += 1;
      const data = docSnap.data() || {};
      const patch = normalizeTransactionPatch(data);
      if (Object.keys(patch).length > 0 && !patchesAreEqual(data, patch)) {
        batch.set(docSnap.ref, patch, { merge: true });
        updatedTransactions += 1;
      }
      const parsed = parseTransactionStatsInput(data);
      if (parsed && !parsed.deleted) {
        const monthKey = monthKeyFromDate(parsed.date);
        const stats = monthly.get(monthKey) || {};
        applyMonthlyDelta(stats, parsed, 1);
        monthly.set(monthKey, stats);
      }
    });
    await batch.commit();
    lastDoc = snap.docs[snap.docs.length - 1];
  }

  const existingStats = await db.collection(`ledgers/${ledgerId}/monthlyStats`).get();
  const deleteBatch = db.batch();
  existingStats.docs.forEach((docSnap) => deleteBatch.delete(docSnap.ref));
  await deleteBatch.commit();

  const entries = Array.from(monthly.entries());
  for (let index = 0; index < entries.length; index += 400) {
    const batch = db.batch();
    entries.slice(index, index + 400).forEach(([monthKey, stats]) => {
      batch.set(db.doc(`ledgers/${ledgerId}/monthlyStats/${monthKey}`), {
        ...stats,
        monthKey,
        year: Number(monthKey.slice(0, 4)),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    });
    await batch.commit();
  }

  return {
    ok: true,
    ledgerId,
    scanned,
    updatedTransactions,
    rebuiltMonths: entries.length
  };
};

export const adminRebuildLedgerStats = onCall(adminRebuildLedgerStatsHandler);

const TAIPEI_TZ = 'Asia/Taipei';
const TAIPEI_OFFSET = '+08:00';

const getDatePartsInTimeZone = (date: Date, timeZone: string) => {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
  const parts = formatter.formatToParts(date);
  const year = parts.find((p) => p.type === 'year')?.value;
  const month = parts.find((p) => p.type === 'month')?.value;
  const day = parts.find((p) => p.type === 'day')?.value;
  if (!year || !month || !day) {
    throw new Error('Failed to resolve date parts for time zone.');
  }
  return { year, month, day };
};

const toTaipeiMidnight = (date: Date) => {
  const { year, month, day } = getDatePartsInTimeZone(date, TAIPEI_TZ);
  return new Date(`${year}-${month}-${day}T00:00:00${TAIPEI_OFFSET}`);
};

const getTaipeiDateKey = (date: Date) => {
  const { year, month, day } = getDatePartsInTimeZone(date, TAIPEI_TZ);
  return `${year}-${month}-${day}`;
};

const parseBaseDate = (value: any) => {
  if (!value) return null;
  if (typeof value === 'string') {
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      return new Date(`${value}T00:00:00${TAIPEI_OFFSET}`);
    }
    const parsed = new Date(value);
    if (!Number.isNaN(parsed.getTime())) {
      return toTaipeiMidnight(parsed);
    }
  }
  if (typeof value?.toDate === 'function') {
    return toTaipeiMidnight(value.toDate());
  }
  if (value instanceof Date) {
    return toTaipeiMidnight(value);
  }
  return null;
};

const addMonthsWithDay = (base: Date, months: number, day: number) => {
  const { year, month } = getDatePartsInTimeZone(base, TAIPEI_TZ);
  const baseYear = Number(year);
  const baseMonthIndex = Number(month) - 1;
  const monthIndex = baseMonthIndex + months;
  const targetYear = baseYear + Math.floor(monthIndex / 12);
  const targetMonth = ((monthIndex % 12) + 12) % 12;
  const daysInMonth = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  const safeDay = Math.min(day, daysInMonth);
  const monthText = String(targetMonth + 1).padStart(2, '0');
  const dayText = String(safeDay).padStart(2, '0');
  return new Date(`${targetYear}-${monthText}-${dayText}T00:00:00${TAIPEI_OFFSET}`);
};

const computeNextRunAt = (baseDate: Date, intervalMonths: number, executeDay: number) => {
  const today = toTaipeiMidnight(new Date());
  const base = toTaipeiMidnight(baseDate);
  const { year, month } = getDatePartsInTimeZone(base, TAIPEI_TZ);
  const baseYear = Number(year);
  const baseMonthIndex = Number(month) - 1;
  const daysInMonth = new Date(Date.UTC(baseYear, baseMonthIndex + 1, 0)).getUTCDate();
  const safeDay = Math.min(executeDay, daysInMonth);
  const monthText = String(baseMonthIndex + 1).padStart(2, '0');
  const dayText = String(safeDay).padStart(2, '0');
  let next = new Date(`${baseYear}-${monthText}-${dayText}T00:00:00${TAIPEI_OFFSET}`);
  if (next < today) {
    next = addMonthsWithDay(next, intervalMonths, executeDay);
  }
  return next;
};

const buildRecurringTransaction = (data: any, ledgerId: string, userId: string, date: Date) => {
  const txType = data.type === 'income' ? 'INCOME' : 'EXPENSE';
  const dateKey = getTaipeiDateKey(date);
  return {
    amount: data.amount || 0,
    type: txType,
    category: data.category || '?��?',
    description: data.title || '?��??�支',
    rewards: 0,
    date: dateKey,
    creatorUid: userId,
    ledgerId,
    createdAt: Date.now(),
    updatedAt: Date.now()
  };
};

export const onRecurringTemplateCreate = functionsV1.firestore
  .document('recurring_templates/{templateId}')
  .onCreate(async (snap, context) => {
    const data = snap.data() || {};
    const ledgerId = data.ledgerId as string | undefined;
    const userId = data.userId as string | undefined;
    const nextRunAtRaw = data.nextRunAt?.toDate?.() as Date | undefined;
    const isActive = data.isActive !== false;
    const remainingRuns = data.remainingRuns as number | undefined;
    const intervalMonths = Math.max(Number(data.intervalMonths) || 1, 1);
    const executeDay = Math.min(Math.max(Number(data.executeDay) || 1, 1), 31);
    const baseDate = parseBaseDate(data.baseDate ?? data.nextRunAt);

    const nextRunAt = baseDate
      ? computeNextRunAt(baseDate, intervalMonths, executeDay)
      : (nextRunAtRaw ? toTaipeiMidnight(nextRunAtRaw) : undefined);
    if (!ledgerId || !userId || !nextRunAt || !isActive) {
      return null;
    }

    if (typeof remainingRuns === 'number' && remainingRuns <= 0) {
      await snap.ref.update({
        isActive: false,
        remainingRuns: 0,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      return null;
    }

    const db = admin.firestore();
    const dateKey = getTaipeiDateKey(nextRunAt);
    const txRef = db.collection(`ledgers/${ledgerId}/transactions`).doc(`recurring_${context.params.templateId}_${dateKey}`);
    const batch = db.batch();
    batch.set(txRef, buildRecurringTransaction(data, ledgerId, userId, nextRunAt));
    batch.update(snap.ref, {
      precreatedFor: admin.firestore.Timestamp.fromDate(nextRunAt),
      nextRunAt: admin.firestore.Timestamp.fromDate(nextRunAt),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    await batch.commit();
    return null;
  });

export const processRecurringTemplates = functionsV1.pubsub
  .schedule('0 3 * * *')
  .timeZone('Asia/Taipei')
  .onRun(async () => {
    await runRecurringTemplatesOnce();
    return null;
  });

const runRecurringTemplatesOnce = async () => {
  const db = admin.firestore();
  const now = admin.firestore.Timestamp.now();
  const snap = await db
    .collection('recurring_templates')
    .where('isActive', '==', true)
    .where('nextRunAt', '<=', now)
    .get();

  if (snap.empty) {
    return { processed: 0 };
  }

  const batch = db.batch();
  let processed = 0;

  snap.docs.forEach((docSnap) => {
    const data = docSnap.data() || {};
    const ledgerId = data.ledgerId as string | undefined;
    const userId = data.userId as string | undefined;
    const nextRunAtRaw = data.nextRunAt?.toDate?.() as Date | undefined;
    const precreatedForRaw = data.precreatedFor?.toDate?.() as Date | undefined;
    const nextRunAt = nextRunAtRaw ? toTaipeiMidnight(nextRunAtRaw) : undefined;
    const precreatedFor = precreatedForRaw ? toTaipeiMidnight(precreatedForRaw) : undefined;

    if (!ledgerId || !userId || !nextRunAt) {
      return;
    }

    processed += 1;

    const remainingRuns = data.remainingRuns as number | undefined;
    if (typeof remainingRuns === 'number' && remainingRuns <= 0) {
      batch.update(docSnap.ref, {
        isActive: false,
        remainingRuns: 0,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      return;
    }

    const nextRunKey = getTaipeiDateKey(nextRunAt);
    const precreatedKey = precreatedFor ? getTaipeiDateKey(precreatedFor) : null;
    if (precreatedKey !== nextRunKey) {
      const txRef = db.collection(`ledgers/${ledgerId}/transactions`).doc(`recurring_${docSnap.id}_${nextRunKey}`);
      batch.set(txRef, buildRecurringTransaction(data, ledgerId, userId, nextRunAt));
    }

    const intervalMonths = Math.max(Number(data.intervalMonths) || 1, 1);
    const executeDay = Math.min(Math.max(Number(data.executeDay) || nextRunAt.getDate(), 1), 31);
    const nextDate = addMonthsWithDay(nextRunAt, intervalMonths, executeDay);
    const updates: Record<string, any> = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    };

    if (typeof remainingRuns === 'number') {
      const nextRemaining = remainingRuns - 1;
      updates.remainingRuns = nextRemaining;
      if (nextRemaining <= 0) {
        updates.isActive = false;
      }
      if (nextRemaining <= 0) {
        updates.precreatedFor = admin.firestore.FieldValue.delete();
      }
    }

    const nextDateKey = getTaipeiDateKey(nextDate);
    const existingPrecreatedKey = precreatedFor ? getTaipeiDateKey(precreatedFor) : null;
    const shouldPrecreateNext = typeof remainingRuns !== 'number' || remainingRuns - 1 > 0;
    const shouldCreateNext = shouldPrecreateNext && existingPrecreatedKey !== nextDateKey;
    if (shouldCreateNext) {
      const nextRef = db.collection(`ledgers/${ledgerId}/transactions`).doc(`recurring_${docSnap.id}_${nextDateKey}`);
      batch.set(nextRef, buildRecurringTransaction(data, ledgerId, userId, nextDate));
      updates.precreatedFor = admin.firestore.Timestamp.fromDate(nextDate);
    }

    updates.nextRunAt = admin.firestore.Timestamp.fromDate(nextDate);
    batch.update(docSnap.ref, updates);
  });

  await batch.commit();
  return { processed };
};

export const adminRunRecurringNow = onCall(async (request) => {
  ensureAdminAccess(request);
  const result = await runRecurringTemplatesOnce();
  return { ok: true, ...result };
});

// 2.1 ?�?��?軟刪??(Leave Ledger)
export const leaveLedgerHandler = async (request: any) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Please sign in');
  }

  const ledgerId = (request.data as any)?.ledgerId as string | undefined;
  if (!ledgerId) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }

  const db = admin.firestore();
  const ledgerRef = db.collection('ledgers').doc(ledgerId);
  const uid = request.auth.uid;

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ledgerRef);
    if (!snap.exists) {
      throw new HttpsError('not-found', 'Not found');
    }

    const data = snap.data() || {};
    const members = Array.isArray(data.members) ? data.members : [];
    const newMembers = members.filter((m: any) => m?.uid !== uid);

    if (newMembers.length === members.length) {
      throw new HttpsError('failed-precondition', 'Failed precondition');
    }

    const updates: Record<string, any> = { members: newMembers };
    if (newMembers.length === 0) {
      const sevenDaysLater = admin.firestore.Timestamp.fromMillis(
        Date.now() + 7 * 24 * 60 * 60 * 1000
      );
      updates.scheduledDeleteAt = sevenDaysLater;
    }

    tx.update(ledgerRef, updates);
  });

  return { ok: true };
};

export const leaveLedger = onCall(leaveLedgerHandler);

// 2.2 ?�入?�復�?(Join Ledger)
export const joinLedgerHandler = async (request: any) => {
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Please sign in');
  }

  const ledgerId = (request.data as any)?.ledgerId as string | undefined;
  if (!ledgerId) {
    throw new HttpsError('invalid-argument', 'Invalid argument');
  }

  const db = admin.firestore();
  const ledgerRef = db.collection('ledgers').doc(ledgerId);
  const uid = request.auth.uid;
  const token = request.auth.token || {};
  const member = {
    uid,
    displayName: token.name ?? null,
    email: token.email ?? null,
    photoURL: token.picture ?? null
  };

  let ledgerName: string | null = null;

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ledgerRef);
    if (!snap.exists) {
      throw new HttpsError('not-found', 'Not found');
    }

    const data = snap.data() || {};
    ledgerName = data.name ?? null;
    const members = Array.isArray(data.members) ? data.members : [];
    const exists = members.some((m: any) => m?.uid === uid);
    const newMembers = exists ? members : [...members, member];

    const updates: Record<string, any> = { members: newMembers };
    if (data.scheduledDeleteAt) {
      updates.scheduledDeleteAt = admin.firestore.FieldValue.delete();
    }

    tx.update(ledgerRef, updates);
  });

  return { ok: true, ledgerName };
};

export const joinLedger = onCall(joinLedgerHandler);

// 2.3 ?��?清�? (Scheduled Cleanup)
export const scheduledCleanup = functionsV1.pubsub
  .schedule('0 2 * * *')
  .timeZone('Asia/Taipei')
  .onRun(async () => {
  const db = admin.firestore();
  const now = admin.firestore.Timestamp.now();
  const snap = await db
    .collection('ledgers')
    .where('members', '==', [])
    .where('scheduledDeleteAt', '<', now)
    .get();

  if (snap.empty) return null;

  const batch = db.batch();
  snap.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
  return null;
});



















