// AI-assisted catalog URL state. Only known sort keys and bounded page numbers are restored.
export function parseCatalogState(hash) {
  const query = new URLSearchParams(hash.split('?')[1] || '');
  const page = Number(query.get('page'));
  const sort = query.get('sort');
  return { keyword: (query.get('q') || '').slice(0,100), category: (query.get('category') || '').slice(0,40), sort: ['featured','newest','price_asc','price_desc'].includes(sort) ? sort : 'featured', page: Number.isInteger(page) && page > 0 && page <= 1000000 ? page : 1, scrollY: 0 };
}
export function catalogHash(state) {
  const query = new URLSearchParams();
  if (state.keyword) query.set('q',state.keyword);
  if (state.category) query.set('category',state.category);
  if (state.sort !== 'featured') query.set('sort',state.sort);
  if (state.page > 1) query.set('page',String(state.page));
  return '#/' + (query.size ? '?' + query.toString() : '');
}
