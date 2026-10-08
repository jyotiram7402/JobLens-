import { api } from '../../services/api/client';
import { endpoints } from '../../services/api/endpoints';
import type { Company } from './types';

export const companiesApi = {
  byId: (companyId: string, signal?: AbortSignal) =>
    api.get<Company>(endpoints.companies.byId(companyId), { signal }),
};
