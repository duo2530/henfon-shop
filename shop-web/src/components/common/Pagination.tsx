import React from 'react';

type PaginationItem = number | 'ellipsis-start' | 'ellipsis-end';

export interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  disabled?: boolean;
}

function buildPaginationItems(currentPage: number, totalPages: number): PaginationItem[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }
  if (currentPage <= 4) {
    return [1, 2, 3, 4, 5, 'ellipsis-end', totalPages];
  }
  if (currentPage >= totalPages - 3) {
    return [1, 'ellipsis-start', totalPages - 4, totalPages - 3, totalPages - 2, totalPages - 1, totalPages];
  }
  return [1, 'ellipsis-start', currentPage - 1, currentPage, currentPage + 1, 'ellipsis-end', totalPages];
}

export const Pagination: React.FC<PaginationProps> = ({ currentPage, totalPages, onPageChange, disabled = false }) => {
  const safeTotalPages = Math.max(totalPages, 1);
  const safeCurrentPage = Math.min(Math.max(currentPage, 1), safeTotalPages);
  const items = buildPaginationItems(safeCurrentPage, safeTotalPages);

  return (
    <div className="flex items-center gap-1.5">
      <button
        type="button"
        onClick={() => onPageChange(Math.max(1, safeCurrentPage - 1))}
        disabled={disabled || safeCurrentPage === 1}
        className="px-2.5 py-1 rounded border border-[#E2E8F0] hover:bg-white disabled:opacity-40 disabled:pointer-events-none transition-colors"
      >
        上一页
      </button>

      {items.map((item) => item === 'ellipsis-start' || item === 'ellipsis-end' ? (
        <span key={item} className="w-7 h-7 flex items-center justify-center text-gray-400">...</span>
      ) : (
        <button
          type="button"
          key={item}
          onClick={() => onPageChange(item)}
          disabled={disabled}
          className={`w-7 h-7 rounded text-xs font-medium transition-all ${
            safeCurrentPage === item
              ? 'bg-[#2563EB] text-white font-bold'
              : 'border border-[#E2E8F0] hover:bg-white text-gray-700'
          }`}
        >
          {item}
        </button>
      ))}

      <button
        type="button"
        onClick={() => onPageChange(Math.min(safeTotalPages, safeCurrentPage + 1))}
        disabled={disabled || safeCurrentPage === safeTotalPages}
        className="px-2.5 py-1 rounded border border-[#E2E8F0] hover:bg-white disabled:opacity-40 disabled:pointer-events-none transition-colors"
      >
        下一页
      </button>
    </div>
  );
};
