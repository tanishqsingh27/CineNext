// HTML pattern uses Unicode sets (the v flag), so the literal hyphen must be escaped.
export const movieSlugPattern = '[a-z0-9\\-]{1,100}';

export function movieSlug(title: string) {
  return title.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 100).replace(/-+$/g, '');
}
