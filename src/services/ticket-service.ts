/** File Hash: e698a3c5b43c6af1ef81eef0755b01b61dd9100b8d0bd57592240a5beea85d49 */

/** Autogenerated Code - Do Not Touch */
/* eslint-disable */

import { useService } from './service';
import type { InvoiceDetail } from "@/dtos/invoice/invoice-detail"
import type { TicketReservationsSearch } from "@/dtos/ticket/ticket-reservations-search"
import type { TicketSeating } from "@/dtos/ticket/ticket-seating"
import type { TicketReservation } from "@/dtos/ticket/ticket-reservation"
import type { TicketMinimal } from "@/dtos/ticket/ticket-minimal"

export function useTicketService() {
  const basePath = `api/v1/tickets`;
  const { api, filterSearchParams } = useService(basePath);
  
  async function cancelAndRefundTicket(id: number): Promise<InvoiceDetail> {
    return api.put(`${id}/cancelation`).json();
  }
  async function cancelTicketReservation(id: number): Promise<void> {
    await api.delete(`${id}`)
  }
  async function getReservationsForSeatingPlan(searchDto: TicketReservationsSearch): Promise<TicketSeating[]> {
    return api.get(`reservations`, { searchParams: filterSearchParams([['seatingPlanId', searchDto.seatingPlanId], ['eventShowId', searchDto.eventShowId]]) }).json();
  }
  async function reserveTickets(ticketDto: TicketReservation): Promise<TicketMinimal[]> {
    return api.post(``, { json: ticketDto }).json();
  }
  return {
    cancelAndRefundTicket,
    cancelTicketReservation,
    getReservationsForSeatingPlan,
    reserveTickets,
  };
}
