/** File Hash: 79b3599840c3d85fde5d93b6a4e514c0ffac4b72420cc1f56f130e3626c4c047 */

/** Autogenerated Code - Do Not Touch */
/* eslint-disable */

import { useService } from './service';
import type { OrderPaymentInfo } from "@/dtos/order/order-payment-info"
import type { InvoiceDetail } from "@/dtos/invoice/invoice-detail"

export function useOrderService() {
  const basePath = `api/v1/orders`;
  const { api, filterSearchParams } = useService(basePath);
  
  async function placeOrder(orderPaymentInfoDto: OrderPaymentInfo): Promise<InvoiceDetail> {
    return api.post(``, { json: orderPaymentInfoDto }).json();
  }
  return {
    placeOrder,
  };
}
