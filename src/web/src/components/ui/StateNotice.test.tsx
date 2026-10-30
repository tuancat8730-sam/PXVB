import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StateNotice } from './StateNotice';

describe('StateNotice (trạng thái màn hình)', () => {
  it('announces loading as a polite status', () => {
    render(<StateNotice kind="loading" />);

    expect(screen.getByRole('status')).toHaveTextContent('Đang tải dữ liệu');
  });

  it('announces an error as an alert with the given message', () => {
    render(<StateNotice kind="error" message="Không kết nối được máy chủ." />);

    expect(screen.getByRole('alert')).toHaveTextContent('Không kết nối được máy chủ.');
  });

  it('explains an empty list', () => {
    render(<StateNotice kind="empty" />);

    expect(screen.getByRole('status')).toHaveTextContent('Chưa có dữ liệu');
  });

  it('warns that data is partial', () => {
    render(<StateNotice kind="partial" message="Chỉ tải được 20 dòng đầu." />);

    expect(screen.getByRole('status')).toHaveTextContent('Chỉ tải được 20 dòng đầu.');
  });

  it('explains that the user has no permission', () => {
    render(<StateNotice kind="noperm" />);

    expect(screen.getByRole('alert')).toHaveTextContent('Bạn không có quyền xem màn hình này.');
  });

  it('confirms a successful save', () => {
    render(<StateNotice kind="success" message="Đã lưu biểu mẫu." />);

    expect(screen.getByRole('status')).toHaveTextContent('Đã lưu biểu mẫu.');
  });
});
