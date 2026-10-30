import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getRecord, updateRecord } from '../../services/md/catalogApi';
import { KsclDesignerScreen } from './KsclDesignerScreen';

vi.mock('../../services/md/catalogApi', () => ({
  getRecord: vi.fn(),
  updateRecord: vi.fn(),
}));

const mockedGet = vi.mocked(getRecord);
const mockedUpdate = vi.mocked(updateRecord);
const FORM_ID = '0195a1b2-0000-7000-8000-0000000000aa';

const FORM = {
  id: FORM_ID,
  code: 'B06.01',
  name: 'Kiểm tra khối lượng điếu',
  versionNo: 4,
  status: 'DRAFT',
  formSchema: {
    schemaVersion: 1,
    sections: [
      {
        title: 'Thông số',
        fields: [
          { code: 'khoi_luong', label: 'Khối lượng', type: 'NUMBER', required: true },
          { code: 'do_cung', label: 'Độ cứng', type: 'NUMBER', required: true },
        ],
      },
    ],
  },
};

function codesInOrder(): string[] {
  const rows = screen.getAllByRole('row').slice(1);
  return rows.map((row) => within(row).getAllByRole('cell')[2].textContent ?? '');
}

describe('KsclDesignerScreen (U-1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGet.mockResolvedValue(FORM);
    mockedUpdate.mockResolvedValue(FORM);
  });

  it('asks the user to pick a form when none is open', () => {
    render(<KsclDesignerScreen recordId={null} />);

    expect(screen.getByText(/Chọn một biểu mẫu/)).toBeInTheDocument();
    expect(mockedGet).not.toHaveBeenCalled();
  });

  it('shows the form code, version and its fields in order', async () => {
    render(<KsclDesignerScreen recordId={FORM_ID} />);

    expect(await screen.findByRole('heading', { name: 'Trình thiết kế B06.01 (nháp v4)' })).toBeInTheDocument();
    expect(codesInOrder()).toEqual(['khoi_luong', 'do_cung']);
  });

  it('moves a field down with the Xuống button', async () => {
    render(<KsclDesignerScreen recordId={FORM_ID} />);
    await screen.findByRole('heading', { name: /Trình thiết kế/ });

    await userEvent.click(screen.getByRole('button', { name: 'Xuống trường khoi_luong' }));

    expect(codesInOrder()).toEqual(['do_cung', 'khoi_luong']);
  });

  it('blocks saving and lists the problems when the schema is invalid', async () => {
    render(<KsclDesignerScreen recordId={FORM_ID} />);
    await screen.findByRole('heading', { name: /Trình thiết kế/ });

    await userEvent.selectOptions(screen.getByLabelText('Kiểu'), 'SELECT');
    await userEvent.type(screen.getByLabelText('Mã trường'), 'chon_co');
    await userEvent.type(screen.getByLabelText('Nhãn'), 'Chọn cỡ');
    await userEvent.click(screen.getByRole('button', { name: 'Thêm trường' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Trường chon_co kiểu SELECT cần danh sách lựa chọn');
    expect(screen.getByRole('button', { name: 'Lưu nháp' })).toBeDisabled();
  });

  it('saves the draft schema to the form and confirms it', async () => {
    render(<KsclDesignerScreen recordId={FORM_ID} />);
    await screen.findByRole('heading', { name: /Trình thiết kế/ });

    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }));

    await waitFor(() => expect(mockedUpdate).toHaveBeenCalledTimes(1));
    expect(mockedUpdate).toHaveBeenCalledWith('qc-form-templates', FORM_ID, {
      formSchema: expect.objectContaining({ schemaVersion: 1 }),
    });
    expect(await screen.findByText('Đã lưu nháp.')).toBeInTheDocument();
  });

  it('shows the server message when the form cannot be loaded', async () => {
    mockedGet.mockRejectedValue(new Error('boom'));

    render(<KsclDesignerScreen recordId={FORM_ID} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
