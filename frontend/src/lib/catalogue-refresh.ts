import type { QueryClient } from '@tanstack/react-query';

// Customers in another tab see an administrator's updates without a hard reload.
export const catalogueRefresh = { staleTime: 0, refetchOnWindowFocus: true, refetchInterval: 15_000 } as const;

export async function refreshCatalogue(client: QueryClient) {
  await Promise.all(['admin', 'movies', 'featured', 'movie', 'filters', 'theatres', 'showtimes'].map(key =>
    client.invalidateQueries({ queryKey: [key] })));
}
