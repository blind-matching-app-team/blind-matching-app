import { delay, http, HttpResponse } from 'msw';
import type { Product } from '../api/payments';
import { setupKey } from '../lib/preferences';

// Scenarios (bma-payment-scenario): success (default) | fail
const failing = () => sessionStorage.getItem('bma-payment-scenario') === 'fail';
const subscribedKey = () => setupKey('subscribed');
const fail = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message }, { status });

// Prices are placeholders, as in the backend PY_PRODUCT seed.
const products: Product[] = [
  {
    productId: 1,
    productCode: 'MATCH_CHANCE',
    productName: '매칭기회 추가 1회',
    productType: 'ITEM',
    price: 1000,
    currency: 'KRW',
    benefit: { itemType: 'MATCH_CHANCE', quantity: 1 },
  },
  {
    productId: 2,
    productCode: 'REMATCH_TICKET',
    productName: '재매칭권 1회',
    productType: 'ITEM',
    price: 2000,
    currency: 'KRW',
    benefit: { itemType: 'REMATCH_TICKET', quantity: 1 },
  },
  {
    productId: 3,
    productCode: 'PREMIUM_MONTHLY',
    productName: '정밀매칭 구독',
    productType: 'SUBSCRIPTION',
    price: 9900,
    currency: 'KRW',
    benefit: {
      monthlyGrants: { MATCH_CHANCE: 3, REMATCH_TICKET: 2 },
      carryOverMonths: 2,
      precisionMatching: true,
      revealWaitSkip: true,
    },
  },
];

const find = async (request: Request, type: Product['productType']) => {
  const { productCode } = (await request.json()) as { productCode?: string };
  return products.find((item) => item.productCode === productCode && item.productType === type);
};

export const paymentHandlers = [
  http.get('/api/mock/payments/products', () => HttpResponse.json({ data: products })),
  http.get('/api/mock/payments/subscription', () =>
    HttpResponse.json({ data: { subscribed: localStorage.getItem(subscribedKey()) === 'true' } }),
  ),
  http.post('/api/mock/payments/consumable', async ({ request }) => {
    await delay(500);
    if (!(await find(request, 'ITEM'))) return fail('PAY_001', '판매 중인 상품이 아니에요.', 404);
    if (failing()) return fail('PAY_002', '결제에 실패했어요 (MOCK_DECLINED)', 400);
    return HttpResponse.json({ data: { payment: { status: 'DONE' } } });
  }),
  http.post('/api/mock/payments/subscription', async ({ request }) => {
    await delay(500);
    if (!(await find(request, 'SUBSCRIPTION')))
      return fail('PAY_001', '판매 중인 상품이 아니에요.', 404);
    if (localStorage.getItem(subscribedKey()) === 'true')
      return fail('PAY_009', '이미 구독 중이에요.', 409);
    if (failing()) return fail('PAY_002', '결제에 실패했어요 (MOCK_DECLINED)', 400);
    localStorage.setItem(subscribedKey(), 'true');
    return HttpResponse.json({ data: { status: 'ACTIVE' } });
  }),
];
