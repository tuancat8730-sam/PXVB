import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import App from './App';
import { MD_SCREENS } from './mdScreens';

describe('App shell', () => {
  it('lists every E08 master-data screen in the navigation', () => {
    render(<App />);

    const nav = screen.getByRole('navigation', { name: 'Danh mục và quản trị' });
    const labels = within(nav).getAllByRole('button').map((button) => button.textContent);

    expect(labels).toEqual(MD_SCREENS.map((screen) => screen.label));
    expect(labels).toHaveLength(7);
  });

  it('opens the chosen screen and marks its navigation item as current', async () => {
    render(<App />);

    await userEvent.click(screen.getByRole('button', { name: 'Lỗi và luật A/B/C' }));

    expect(screen.getByRole('button', { name: 'Lỗi và luật A/B/C' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: 'Biểu mẫu KSCL' })).not.toHaveAttribute('aria-current');
  });

  it('shows the first screen by default', () => {
    render(<App />);

    expect(screen.getByRole('button', { name: 'Biểu mẫu KSCL' })).toHaveAttribute('aria-current', 'page');
  });
});
