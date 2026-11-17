import { filenameFromDisposition, saveFile } from './save-file';

describe('saveFile', () => {
  afterEach(() => vi.restoreAllMocks());

  it('should click a temporary download link named after the file, then clean up', () => {
    // jsdom n'implémente pas les URL d'objets : on les simule.
    URL.createObjectURL = vi.fn(() => 'blob:export');
    URL.revokeObjectURL = vi.fn();
    const clicked: HTMLAnchorElement[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this);
    });

    saveFile(new Blob(['x']), 'EventProxy-2026-11-17-publication-7.xlsx');

    expect(clicked).toHaveLength(1);
    expect(clicked[0].download).toBe('EventProxy-2026-11-17-publication-7.xlsx');
    expect(clicked[0].href).toBe('blob:export');
    expect(document.querySelector('a[download]')).toBeNull();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export');
  });
});

describe('filenameFromDisposition', () => {
  it('should read a quoted or bare file name', () => {
    expect(filenameFromDisposition('attachment; filename="export.xlsx"')).toBe('export.xlsx');
    expect(filenameFromDisposition('attachment; filename=export.xlsx')).toBe('export.xlsx');
  });

  it('should return null without a file name', () => {
    expect(filenameFromDisposition(null)).toBeNull();
    expect(filenameFromDisposition('attachment')).toBeNull();
  });
});
