"use client";

import {
  BarChart3,
  ArrowLeft,
  ArrowRight,
  Camera,
  House,
  LoaderCircle,
  Pencil,
  Plus,
  ReceiptText,
  RotateCcw,
  Search,
  Settings,
  SlidersHorizontal,
  Tags,
  Trash2,
  X,
} from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { mutation, multipartMutation, request } from "../../lib/api";
import { CurrentUserAvatar } from "../current-user-avatar";
import { CalendarPicker } from "../ui/date-picker";
import { CustomSelect } from "../ui/custom-select";
import { ImageCropDialog } from "../ui/image-crop-dialog";

type Category = { id: string; name: string; sortOrder: number; version: number };
type Expense = {
  id: string;
  amount: number;
  spentOn: string;
  category: Pick<Category, "id" | "name"> | null;
  merchant: string | null;
  memo: string | null;
  images?: ExpenseImage[];
  version: number;
  createdAt: string;
  updatedAt: string;
};
type ExpenseImage = { id: string; contentUrl: string; sortOrder: number };
type DraftImage = ExpenseImage & { temporary: boolean };
type UploadedImage = { id: string; contentUrl: string; status: "TEMP"; purpose: "EXPENSE" };
type ExpensePage = { items: Expense[]; nextCursor: string | null; hasNext: boolean };
type Filters = { from: string; to: string; categoryId: string; keyword: string };
type ExpenseDraft = {
  amount: string;
  spentOn: string;
  categoryId: string;
  merchant: string;
  memo: string;
};

const won = new Intl.NumberFormat("ko-KR");
const initialFilters: Filters = { from: "", to: "", categoryId: "", keyword: "" };

function todayInSeoul() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());
}

async function uploadExpenseImage(file: File): Promise<UploadedImage> {
  const body = new FormData();
  body.append("file", file);
  return multipartMutation<UploadedImage>("/api/v1/uploads/images?purpose=EXPENSE", body);
}

function expenseQuery(filters: Filters, cursor?: string) {
  const params = new URLSearchParams({ size: "20" });
  if (filters.from) params.set("from", filters.from);
  if (filters.to) params.set("to", filters.to);
  if (filters.categoryId === "uncategorized") params.set("uncategorized", "true");
  else if (filters.categoryId) params.set("categoryId", filters.categoryId);
  if (filters.keyword.trim()) params.set("keyword", filters.keyword.trim());
  if (cursor) params.set("cursor", cursor);
  return `/api/v1/expenses?${params}`;
}

export function ExpenseManager() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [filters, setFilters] = useState(initialFilters);
  const [appliedFilters, setAppliedFilters] = useState(initialFilters);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [isExpenseOpen, setIsExpenseOpen] = useState(false);
  const [isMobileFilterOpen, setIsMobileFilterOpen] = useState(false);
  const [editingExpense, setEditingExpense] = useState<Expense | null>(null);
  const [draftImages, setDraftImages] = useState<DraftImage[]>([]);
  const [pendingCropFiles, setPendingCropFiles] = useState<File[]>([]);
  const [draft, setDraft] = useState<ExpenseDraft>({
    amount: "",
    spentOn: todayInSeoul(),
    categoryId: "",
    merchant: "",
    memo: "",
  });

  useEffect(() => {
    let active = true;
    Promise.all([
      request<{ items: Category[] }>("/api/v1/categories"),
      request<ExpensePage>(expenseQuery(initialFilters)),
    ])
      .then(([categoryData, expenseData]) => {
        if (!active) return;
        setCategories(categoryData.items);
        setExpenses(expenseData.items);
        setNextCursor(expenseData.nextCursor);
        setHasNext(expenseData.hasNext);
        if (new URLSearchParams(window.location.search).get("new") === "1") {
          setDraft({
            amount: "",
            spentOn: todayInSeoul(),
            categoryId: categoryData.items[0]?.id ?? "",
            merchant: "",
            memo: "",
          });
          setIsExpenseOpen(true);
        }
      })
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

  const loadedTotal = useMemo(
    () => expenses.reduce((sum, expense) => sum + expense.amount, 0),
    [expenses],
  );
  const appliedCategoryLabel = appliedFilters.categoryId === "uncategorized"
    ? "분류 없음"
    : categories.find((category) => category.id === appliedFilters.categoryId)?.name;
  const activeMobileFilterCount =
    Number(Boolean(appliedFilters.from || appliedFilters.to)) + Number(Boolean(appliedFilters.categoryId));

  useEffect(() => {
    if (!isMobileFilterOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setFilters(appliedFilters);
        setIsMobileFilterOpen(false);
      }
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", closeOnEscape);
    };
  }, [appliedFilters, isMobileFilterOpen]);

  async function refreshExpenses(nextFilters: Filters = appliedFilters) {
    setIsLoading(true);
    setError(null);
    try {
      const page = await request<ExpensePage>(expenseQuery(nextFilters));
      setExpenses(page.items);
      setNextCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "식비를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }

  function openCreate() {
    setEditingExpense(null);
    setDraft({
      amount: "",
      spentOn: todayInSeoul(),
      categoryId: categories[0]?.id ?? "",
      merchant: "",
      memo: "",
    });
    setDraftImages([]);
    setIsExpenseOpen(true);
  }

  function openEdit(expense: Expense) {
    setEditingExpense(expense);
    setDraft({
      amount: String(expense.amount),
      spentOn: expense.spentOn,
      categoryId: expense.category?.id ?? categories[0]?.id ?? "",
      merchant: expense.merchant ?? "",
      memo: expense.memo ?? "",
    });
    setDraftImages((expense.images ?? []).map((image) => ({ ...image, temporary: false })));
    setIsExpenseOpen(true);
  }

  async function saveExpense(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSaving(true);
    setError(null);
    try {
      const body = {
        amount: Number(draft.amount),
        spentOn: draft.spentOn,
        categoryId: draft.categoryId,
        merchant: draft.merchant,
        memo: draft.memo,
        imageIds: draftImages.map((image) => image.id),
        ...(editingExpense ? { version: editingExpense.version } : {}),
      };
      if (editingExpense) {
        await mutation(`/api/v1/expenses/${editingExpense.id}`, "PUT", body);
        setNotice("식비 내역을 수정했습니다.");
      } else {
        await mutation("/api/v1/expenses", "POST", body);
        setNotice("식비 내역을 등록했습니다.");
      }
      setDraftImages([]);
      setIsExpenseOpen(false);
      await refreshExpenses();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "식비를 저장하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  function selectImages(files: FileList | null) {
    if (!files?.length) return;
    const selected = Array.from(files);
    if (draftImages.length + selected.length > 3) {
      setError("식비에는 이미지를 최대 3장까지 등록할 수 있습니다.");
      return;
    }
    const invalid = selected.find((file) => !["image/jpeg", "image/png", "image/webp"].includes(file.type));
    if (invalid) {
      setError("JPEG, PNG 또는 WebP 이미지만 등록할 수 있습니다.");
      return;
    }
    if (selected.some((file) => file.size > 5 * 1024 * 1024)) {
      setError("이미지는 파일당 5MB 이하여야 합니다.");
      return;
    }
    setError(null);
    setPendingCropFiles(selected);
  }

  async function uploadCroppedImage(file: File) {
    setIsUploading(true);
    setError(null);
    try {
      const image = await uploadExpenseImage(file);
      setDraftImages((current) => [
        ...current,
        { ...image, sortOrder: current.length, temporary: true },
      ]);
      setPendingCropFiles((current) => current.slice(1));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "이미지를 업로드하지 못했습니다.");
      setPendingCropFiles([]);
    } finally {
      setIsUploading(false);
    }
  }

  async function removeDraftImage(index: number) {
    const target = draftImages[index];
    setDraftImages((current) => current.filter((_, imageIndex) => imageIndex !== index));
    if (target.temporary) {
      try {
        await mutation(`/api/v1/uploads/images/${target.id}`, "DELETE");
      } catch {
        // The server's scheduled cleanup removes abandoned temporary images.
      }
    }
  }

  function moveDraftImage(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= draftImages.length) return;
    setDraftImages((current) => {
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  function closeExpenseModal() {
    for (const image of draftImages.filter((item) => item.temporary)) {
      void mutation(`/api/v1/uploads/images/${image.id}`, "DELETE").catch(() => undefined);
    }
    setDraftImages([]);
    setPendingCropFiles([]);
    setIsExpenseOpen(false);
  }

  async function deleteExpense(expense: Expense) {
    if (!window.confirm(`${expense.merchant ?? "선택한 식비"} 내역을 삭제할까요?`)) return;
    setError(null);
    try {
      await mutation(`/api/v1/expenses/${expense.id}?version=${expense.version}`, "DELETE");
      setNotice("식비 내역을 삭제했습니다.");
      await refreshExpenses();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "식비를 삭제하지 못했습니다.");
    }
  }

  async function loadMore() {
    if (!nextCursor) return;
    setIsLoading(true);
    try {
      const page = await request<ExpensePage>(expenseQuery(appliedFilters, nextCursor));
      setExpenses((current) => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "다음 식비를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAppliedFilters(filters);
    void refreshExpenses(filters);
  }

  function resetFilters() {
    setFilters(initialFilters);
    setAppliedFilters(initialFilters);
    void refreshExpenses(initialFilters);
  }

  function openMobileFilters() {
    setFilters(appliedFilters);
    setIsMobileFilterOpen(true);
  }

  function closeMobileFilters() {
    setFilters(appliedFilters);
    setIsMobileFilterOpen(false);
  }

  function applyMobileFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAppliedFilters(filters);
    setIsMobileFilterOpen(false);
    void refreshExpenses(filters);
  }

  function clearMobileFilterFields() {
    setFilters({ ...filters, from: "", to: "", categoryId: "" });
  }

  function removeMobileFilter(kind: "period" | "category") {
    const nextFilters = kind === "period"
      ? { ...appliedFilters, from: "", to: "" }
      : { ...appliedFilters, categoryId: "" };
    setFilters(nextFilters);
    setAppliedFilters(nextFilters);
    void refreshExpenses(nextFilters);
  }

  function mobilePeriodLabel() {
    const compact = (value: string) => {
      const [, month, day] = value.split("-");
      return `${Number(month)}.${Number(day)}`;
    };
    if (appliedFilters.from && appliedFilters.to) {
      return `${compact(appliedFilters.from)}~${compact(appliedFilters.to)}`;
    }
    if (appliedFilters.from) return `${compact(appliedFilters.from)}부터`;
    return `${compact(appliedFilters.to)}까지`;
  }

  return (
    <main className="app-shell expense-shell">
      <header className="topbar">
        <Link className="brand" href="/" aria-label="MealBudgetDiet 홈">
          <span className="brand-mark">M</span>
          <span>MealBudgetDiet</span>
        </Link>
        <CurrentUserAvatar />
      </header>

      <section className="expense-hero">
        <div>
          <p className="eyebrow">SHARED LEDGER</p>
          <h1>식비 내역</h1>
          <p>함께 사용한 식비를 기록하고 필요한 내역을 찾아보세요.</p>
        </div>
        <button className="expense-add-button" type="button" onClick={openCreate}>
          <Plus size={19} /> 식비 등록
        </button>
      </section>

      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
      {notice && (
        <button className="message-banner message-banner--notice" type="button" onClick={() => setNotice(null)}>
          {notice}<X size={16} />
        </button>
      )}

      <div className="expense-layout">
        <div className="expense-main-column">
          <div className="mobile-filter-shell">
            <form className="mobile-filter-toolbar" onSubmit={applyFilters}>
              <span className="mobile-keyword-field">
                <Search aria-hidden="true" size={17} />
                <input
                  aria-label="상호명 또는 메모 검색"
                  onChange={(event) => setFilters({ ...filters, keyword: event.target.value })}
                  placeholder="상호명 또는 메모 검색"
                  value={filters.keyword}
                />
              </span>
              <button
                aria-expanded={isMobileFilterOpen}
                aria-haspopup="dialog"
                className="mobile-filter-button"
                onClick={openMobileFilters}
                type="button"
              >
                <SlidersHorizontal size={17} />
                필터
                {activeMobileFilterCount > 0 && <span>{activeMobileFilterCount}</span>}
              </button>
            </form>
            {activeMobileFilterCount > 0 && (
              <div aria-label="적용된 검색 조건" className="mobile-filter-chips">
                {(appliedFilters.from || appliedFilters.to) && (
                  <button aria-label="기간 필터 제거" onClick={() => removeMobileFilter("period")} type="button">
                    {mobilePeriodLabel()}<X size={14} />
                  </button>
                )}
                {appliedCategoryLabel && (
                  <button aria-label="카테고리 필터 제거" onClick={() => removeMobileFilter("category")} type="button">
                    {appliedCategoryLabel}<X size={14} />
                  </button>
                )}
              </div>
            )}
          </div>

          <form className="filter-panel desktop-filter-panel" onSubmit={applyFilters}>
            <div className="filter-title"><SlidersHorizontal size={18} /><strong>검색 조건</strong></div>
            <label>
              <span>시작일</span>
              <CalendarPicker ariaLabel="검색 시작일" onChange={(from) => setFilters({ ...filters, from })} value={filters.from} />
            </label>
            <label>
              <span>종료일</span>
              <CalendarPicker ariaLabel="검색 종료일" onChange={(to) => setFilters({ ...filters, to })} value={filters.to} />
            </label>
            <label>
              <span>카테고리</span>
              <CustomSelect
                ariaLabel="검색 카테고리"
                onChange={(categoryId) => setFilters({ ...filters, categoryId })}
                options={[
                  { value: "", label: "전체 카테고리" },
                  ...categories.map((category) => ({ value: category.id, label: category.name })),
                  { value: "uncategorized", label: "분류 없음" },
                ]}
                value={filters.categoryId}
              />
            </label>
            <label className="keyword-field">
              <span>검색어</span>
              <span className="input-with-icon"><Search size={17} /><input value={filters.keyword} placeholder="상호명 또는 메모" onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} /></span>
            </label>
            <div className="filter-actions">
              <button className="secondary-button" type="button" onClick={resetFilters}><RotateCcw size={16} />초기화</button>
              <button className="dark-button" type="submit"><Search size={16} />검색</button>
            </div>
          </form>

          {isMobileFilterOpen && (
            <div
              className="mobile-filter-backdrop"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget) closeMobileFilters();
              }}
              role="presentation"
            >
              <section aria-labelledby="mobile-filter-title" aria-modal="true" className="mobile-filter-sheet" role="dialog">
                <header>
                  <div>
                    <p className="eyebrow">SEARCH FILTER</p>
                    <h2 id="mobile-filter-title">검색 조건</h2>
                  </div>
                  <button aria-label="검색 조건 닫기" onClick={closeMobileFilters} type="button"><X size={21} /></button>
                </header>
                <form onSubmit={applyMobileFilters}>
                  <div className="mobile-filter-fields">
                    <label>
                      <span>시작일</span>
                      <CalendarPicker ariaLabel="모바일 검색 시작일" onChange={(from) => setFilters({ ...filters, from })} value={filters.from} />
                    </label>
                    <label>
                      <span>종료일</span>
                      <CalendarPicker ariaLabel="모바일 검색 종료일" onChange={(to) => setFilters({ ...filters, to })} value={filters.to} />
                    </label>
                    <label>
                      <span>카테고리</span>
                      <CustomSelect
                        ariaLabel="모바일 검색 카테고리"
                        onChange={(categoryId) => setFilters({ ...filters, categoryId })}
                        options={[
                          { value: "", label: "전체 카테고리" },
                          ...categories.map((category) => ({ value: category.id, label: category.name })),
                          { value: "uncategorized", label: "분류 없음" },
                        ]}
                        value={filters.categoryId}
                                                                />
                    </label>
                  </div>
                  <footer>
                    <button className="secondary-button" onClick={clearMobileFilterFields} type="button"><RotateCcw size={16} />초기화</button>
                    <button className="dark-button" type="submit"><Search size={16} />적용</button>
                  </footer>
                </form>
              </section>
            </div>
          )}

          <section className="expense-results" aria-labelledby="expense-results-title">
            <div className="results-heading">
              <div>
                <p className="eyebrow">내역</p>
                <h2 id="expense-results-title">{expenses.length}건 · {won.format(loadedTotal)}원</h2>
              </div>
              {isLoading && <LoaderCircle className="spin" aria-label="불러오는 중" size={22} />}
            </div>

            {!isLoading && expenses.length === 0 ? (
              <div className="expense-empty"><ReceiptText size={28} /><strong>조건에 맞는 식비가 없습니다.</strong><span>첫 식비를 등록해 보세요.</span></div>
            ) : (
              <ul className="expense-records">
                {expenses.map((expense) => (
                  <li key={expense.id}>
                    {expense.images?.[0] ? (
                      <Image alt="" className="expense-thumbnail" height={58} src={expense.images[0].contentUrl} unoptimized width={58} />
                    ) : (
                      <div className="expense-date-box"><strong>{expense.spentOn.slice(8)}</strong><span>{expense.spentOn.slice(5, 7)}월</span></div>
                    )}
                    <div className="expense-record-copy">
                      <div><span className="category-tag">{expense.category?.name ?? "분류 없음"}</span><span className="expense-full-date">{expense.spentOn.replaceAll("-", ".")}</span></div>
                      <strong>{expense.merchant ?? "상호명 없음"}</strong>
                      {expense.memo && <p>{expense.memo}</p>}
                    </div>
                    <strong className="expense-record-amount">-{won.format(expense.amount)}원</strong>
                    <div className="record-actions">
                      <button type="button" aria-label={`${expense.merchant ?? "식비"} 수정`} onClick={() => openEdit(expense)}><Pencil size={16} /></button>
                      <button type="button" aria-label={`${expense.merchant ?? "식비"} 삭제`} onClick={() => void deleteExpense(expense)}><Trash2 size={16} /></button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            {hasNext && <button className="load-more-button" type="button" disabled={isLoading} onClick={() => void loadMore()}>내역 더 보기</button>}
          </section>
        </div>

        <aside className="category-panel" aria-labelledby="category-title">
          <div className="category-heading"><span><Tags size={19} /></span><div><p className="eyebrow">CATEGORY</p><h2 id="category-title">카테고리</h2></div><Link className="category-manage-link" href="/settings/categories">설정에서 관리</Link></div>
          <ul>
            {categories.map((category) => (
              <li key={category.id}>
                <span className="category-order">{String(category.sortOrder).padStart(2, "0")}</span>
                <strong>{category.name}</strong>
              </li>
            ))}
          </ul>
          <p className="category-help">추가·수정·삭제는 설정의 카테고리 관리에서 할 수 있습니다.</p>
        </aside>
      </div>

      <nav className="bottom-nav" aria-label="주요 메뉴">
        <Link href="/"><House size={20} /><span>홈</span></Link>
        <Link className="is-active" href="/expenses"><ReceiptText size={20} /><span>식비</span></Link>
        <Link href="/statistics"><BarChart3 size={20} /><span>통계</span></Link>
        <Link href="/settings"><Settings size={20} /><span>설정</span></Link>
      </nav>

      {isExpenseOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) closeExpenseModal(); }}>
          <section className="expense-modal" role="dialog" aria-modal="true" aria-labelledby="expense-form-title">
            <div className="modal-heading"><div><p className="eyebrow">EXPENSE</p><h2 id="expense-form-title">{editingExpense ? "식비 수정" : "식비 등록"}</h2></div><button type="button" aria-label="닫기" onClick={closeExpenseModal}><X size={20} /></button></div>
            <form onSubmit={saveExpense}>
              <label className="amount-field"><span>금액</span><span><input required min={1} inputMode="numeric" type="number" value={draft.amount} onChange={(event) => setDraft({ ...draft, amount: event.target.value })} placeholder="0" /><b>원</b></span></label>
              <div className="form-row">
                <label><span>사용 날짜</span><CalendarPicker ariaLabel="사용 날짜" onChange={(spentOn) => setDraft({ ...draft, spentOn })} value={draft.spentOn} /></label>
                <label>
                  <span>카테고리</span>
                  <CustomSelect
                    ariaLabel="식비 카테고리"
                    onChange={(categoryId) => setDraft({ ...draft, categoryId })}
                    options={categories.map((category) => ({ value: category.id, label: category.name }))}
                    placeholder="선택"
                    value={draft.categoryId}
                  />
                </label>
              </div>
              <label><span>상호명 <small>선택</small></span><input maxLength={100} value={draft.merchant} onChange={(event) => setDraft({ ...draft, merchant: event.target.value })} placeholder="예: 동네마트" /></label>
              <label><span>메모 <small>선택</small></span><textarea maxLength={1000} value={draft.memo} onChange={(event) => setDraft({ ...draft, memo: event.target.value })} placeholder="함께 기억할 내용을 적어두세요." /></label>
              <fieldset className="expense-image-field">
                <legend>이미지 <small>선택 · 최대 3장 · 장당 5MB</small></legend>
                {draftImages.length > 0 && (
                  <ul className="expense-image-previews">
                    {draftImages.map((image, index) => (
                      <li key={image.id}>
                        <Image alt={`첨부 이미지 ${index + 1}`} fill sizes="112px" src={image.contentUrl} unoptimized />
                        <span className="expense-image-order">{index + 1}</span>
                        <div>
                          <button aria-label="앞으로 이동" disabled={index === 0} onClick={() => moveDraftImage(index, -1)} type="button"><ArrowLeft size={15} /></button>
                          <button aria-label="뒤로 이동" disabled={index === draftImages.length - 1} onClick={() => moveDraftImage(index, 1)} type="button"><ArrowRight size={15} /></button>
                          <button aria-label="이미지 제거" onClick={() => void removeDraftImage(index)} type="button"><Trash2 size={15} /></button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
                {draftImages.length < 3 && (
                  <label className="expense-image-picker">
                    {isUploading ? <LoaderCircle className="spin" size={20} /> : <Camera size={20} />}
                    <span>{isUploading ? "이미지 처리 중" : "이미지 추가"}</span>
                    <input accept="image/jpeg,image/png,image/webp" disabled={isUploading || pendingCropFiles.length > 0} multiple onChange={(event) => { selectImages(event.target.files); event.target.value = ""; }} type="file" />
                  </label>
                )}
              </fieldset>
              <div className="modal-actions"><button className="secondary-button" type="button" onClick={closeExpenseModal}>취소</button><button className="expense-add-button" type="submit" disabled={isSaving || isUploading}>{isSaving && <LoaderCircle className="spin" size={17} />}{editingExpense ? "수정 저장" : "등록하기"}</button></div>
            </form>
          </section>
        </div>
      )}
      {pendingCropFiles[0] && (
        <ImageCropDialog
          aspectRatio={4 / 3}
          file={pendingCropFiles[0]}
          onCancel={() => setPendingCropFiles([])}
          onConfirm={uploadCroppedImage}
          title={pendingCropFiles.length > 1 ? `사진 크롭 (${draftImages.length + 1}/${draftImages.length + pendingCropFiles.length})` : "사진 크롭"}
        />
      )}
    </main>
  );
}
