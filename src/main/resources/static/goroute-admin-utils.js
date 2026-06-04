/**
 * Shared admin list helpers â€” avoid full-page loading flicker on mutations.
 */
(function (global) {
  function upsertById(list, item, idKey = 'id') {
    if (!item || item[idKey] == null) return list;
    const id = item[idKey];
    const idx = list.findIndex((row) => row[idKey] === id);
    if (idx >= 0) {
      list[idx] = item;
    } else {
      list.unshift(item);
    }
    return list;
  }

  function removeById(list, id, idKey = 'id') {
    const idx = list.findIndex((row) => row[idKey] === id);
    if (idx >= 0) list.splice(idx, 1);
    return list;
  }

  function upsertManyById(list, items, idKey = 'id') {
    if (!Array.isArray(items)) return list;
    items.forEach((item) => upsertById(list, item, idKey));
    return list;
  }

  /**
   * @param {object} opts
   * @param {boolean} opts.silent - skip loading overlay / table hide
   * @param {HTMLElement} opts.loadingEl
   * @param {HTMLElement} opts.emptyEl
   * @param {HTMLElement} opts.tableEl
   * @param {number} opts.rowCount
   */
  function syncTableChrome(opts) {
    const { silent, loadingEl, emptyEl, tableEl, rowCount } = opts;
    if (loadingEl) loadingEl.style.display = 'none';
    if (rowCount === 0) {
      if (emptyEl) emptyEl.style.display = 'block';
      if (tableEl) tableEl.style.display = 'none';
    } else {
      if (emptyEl) emptyEl.style.display = 'none';
      if (tableEl) tableEl.style.display = 'block';
    }
    if (!silent && loadingEl) loadingEl.style.display = 'none';
  }

  function beginLoading(opts) {
    const { silent, loadingEl, emptyEl, tableEl } = opts;
    if (silent) return;
    if (loadingEl) loadingEl.style.display = 'block';
    if (emptyEl) emptyEl.style.display = 'none';
    if (tableEl) tableEl.style.display = 'none';
  }

  global.GorouteAdmin = {
    upsertById,
    removeById,
    upsertManyById,
    syncTableChrome,
    beginLoading,
  };
})(typeof window !== 'undefined' ? window : globalThis);
