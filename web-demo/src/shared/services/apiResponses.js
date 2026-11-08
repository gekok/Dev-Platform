import api from './axios.js';

const API_PAGE_SIZE = 100;

export function unwrapApiResponse(body) {
  if (
    body &&
    typeof body === 'object' &&
    !Array.isArray(body) &&
    Object.prototype.hasOwnProperty.call(body, 'data')
  ) {
    return body.data;
  }

  return body;
}

export function normalizeApiPage(body, requestedPage = 0, requestedSize = API_PAGE_SIZE) {
  const data = unwrapApiResponse(body);
  if (Array.isArray(data)) {
    return {
      items: data,
      page: requestedPage,
      size: data.length,
      totalElements: data.length,
      totalPages: data.length === 0 ? 0 : 1,
    };
  }

  if (!Array.isArray(data?.items)) {
    throw new TypeError('The API response did not contain a list of items.');
  }

  const totalElements = Number.isInteger(data.totalElements) && data.totalElements >= 0
    ? data.totalElements
    : data.items.length;
  const size = Number.isInteger(data.size) && data.size > 0
    ? data.size
    : requestedSize;
  const totalPages = Number.isInteger(data.totalPages) && data.totalPages >= 0
    ? data.totalPages
    : Math.ceil(totalElements / size);

  return {
    items: data.items,
    page: Number.isInteger(data.page) && data.page >= 0 ? data.page : requestedPage,
    size,
    totalElements,
    totalPages,
  };
}

export async function listAllApiPages(path, params = {}) {
  const firstResponse = await api.get(path, {
    params: { ...params, page: 0, size: API_PAGE_SIZE },
  });
  const firstPage = normalizeApiPage(firstResponse.data, 0, API_PAGE_SIZE);
  const laterPageNumbers = Array.from(
    { length: Math.max(0, firstPage.totalPages - 1) },
    (_, index) => index + 1,
  );
  const remainingPages = await Promise.all(laterPageNumbers.map(async (pageNumber) => {
    const response = await api.get(path, {
      params: { ...params, page: pageNumber, size: API_PAGE_SIZE },
    });
    return normalizeApiPage(response.data, pageNumber, API_PAGE_SIZE);
  }));

  return {
    items: [firstPage.items, ...remainingPages.map((page) => page.items)].flat(),
    pagination: {
      page: firstPage.page,
      size: firstPage.size,
      totalElements: firstPage.totalElements,
      totalPages: firstPage.totalPages,
      pagesLoaded: 1 + remainingPages.length,
    },
  };
}
