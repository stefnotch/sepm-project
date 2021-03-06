/** File Hash: 14ae8f6c421b26de2d72759eee7a912acd21fc9e78afdf55a8e52b4b9f20fff8 */

/** Autogenerated Code - Do Not Touch */
/* eslint-disable */

import { useService } from './service';
import type { LocationSearch } from "@/dtos/location/location-search"
import type { Page } from "@/dtos/page"
import type { Pagination } from "@/dtos/pagination"
import type { LocationDetail } from "@/dtos/location/location-detail"

export function useLocationService() {
  const basePath = `api/v1/location`;
  const { api, filterSearchParams } = useService(basePath);
  
  async function findByFilters(locationSearchDto: LocationSearch, pageDto: Page): Promise<Pagination<LocationDetail>> {
    return api.get(``, { searchParams: filterSearchParams([['postalCode', locationSearchDto.postalCode], ['pageSize', pageDto.pageSize], ['address', locationSearchDto.address], ['city', locationSearchDto.city], ['country', locationSearchDto.country], ['name', locationSearchDto.name], ['pageIndex', pageDto.pageIndex]]) }).json();
  }
  async function findById(id: number): Promise<LocationDetail> {
    return api.get(`${id}`).json();
  }
  return {
    findByFilters,
    findById,
  };
}
