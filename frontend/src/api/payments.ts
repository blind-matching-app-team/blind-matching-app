import { apiRequest } from '../lib/auth';

// Mirrors backend/docs/PAYMENT_API.md (BMA-84).
export type Product = {
  productId: number;
  productCode: string;
  productName: string;
  productType: 'ITEM' | 'SUBSCRIPTION';
  price: number;
  currency: string;
  benefit: {
    itemType?: string;
    quantity?: number;
    monthlyGrants?: Record<string, number>;
    carryOverMonths?: number;
    precisionMatching?: boolean;
    revealWaitSkip?: boolean;
  };
};

// Phase 2 contract; swap /api/mock for /api/v1 when the payment widget is integrated.
const BASE = '/api/mock/payments';

export async function readProducts(signal: AbortSignal) {
  const result = await apiRequest<{ data: Product[] }>(`${BASE}/products`, { signal }, true);
  return result.data;
}

export async function readSubscription(signal: AbortSignal) {
  const result = await apiRequest<{ data: { subscribed: boolean } }>(
    `${BASE}/subscription`,
    { signal },
    true,
  );
  return result.data;
}

// The real flow first opens the Toss payment widget and sends its orderId/paymentKey;
// the mock settles the order directly.
export async function purchaseItem(productCode: string) {
  await apiRequest(
    `${BASE}/consumable`,
    { method: 'POST', body: JSON.stringify({ productCode }) },
    true,
  );
}

export async function subscribe(productCode: string) {
  await apiRequest(
    `${BASE}/subscription`,
    { method: 'POST', body: JSON.stringify({ productCode }) },
    true,
  );
}
