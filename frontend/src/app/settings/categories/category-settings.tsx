"use client";

import {
  CircleAlert,
  FolderCog,
  LoaderCircle,
  Pencil,
  Plus,
  ShieldCheck,
  Tags,
  Trash2,
  X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { mutation, request } from "../../../lib/api";
import { SettingsPageFrame } from "../settings-page-frame";

type Category = { id: string; name: string; sortOrder: number; version: number };
type Ledger = { currentUserRole: "ADMIN" | "MEMBER" };
type CategoryDraft = { id?: string; name: string; sortOrder: string; version?: number };

export function CategorySettings() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [role, setRole] = useState<Ledger["currentUserRole"]>("MEMBER");
  const [draft, setDraft] = useState<CategoryDraft | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadCategories = useCallback(async () => {
    const data = await request<{ items: Category[] }>("/api/v1/categories");
    setCategories(data.items);
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([
      request<{ items: Category[] }>("/api/v1/categories"),
      request<Ledger>("/api/v1/ledger"),
    ])
      .then(([categoryData, ledger]) => {
        if (!active) return;
        setCategories(categoryData.items);
        setRole(ledger.currentUserRole);
      })
      .catch((reason: Error) => active && setError(reason.message))
      .finally(() => active && setIsLoading(false));
    return () => { active = false; };
  }, []);

  const nextSortOrder = useMemo(
    () => Math.max(0, ...categories.map((category) => category.sortOrder)) + 1,
    [categories],
  );

  function openCreate() {
    setError(null);
    setDraft({ name: "", sortOrder: String(nextSortOrder) });
  }

  function openEdit(category: Category) {
    setError(null);
    setDraft({
      id: category.id,
      name: category.name,
      sortOrder: String(category.sortOrder),
      version: category.version,
    });
  }

  async function saveCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!draft || role !== "ADMIN") return;
    setIsSaving(true);
    setError(null);
    setNotice(null);
    try {
      const body = { name: draft.name.trim(), sortOrder: Number(draft.sortOrder) };
      if (draft.id) {
        await mutation(`/api/v1/categories/${draft.id}`, "PUT", { ...body, version: draft.version });
        setNotice(`${body.name} 카테고리를 수정했습니다.`);
      } else {
        await mutation("/api/v1/categories", "POST", body);
        setNotice(`${body.name} 카테고리를 추가했습니다.`);
      }
      setDraft(null);
      await loadCategories();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "카테고리를 저장하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  async function deleteCategory() {
    if (!deleteTarget || role !== "ADMIN") return;
    setIsSaving(true);
    setError(null);
    setNotice(null);
    try {
      await mutation(`/api/v1/categories/${deleteTarget.id}?version=${deleteTarget.version}`, "DELETE");
      setNotice(`${deleteTarget.name} 카테고리를 삭제했습니다.`);
      setDeleteTarget(null);
      await loadCategories();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "카테고리를 삭제하지 못했습니다.");
    } finally {
      setIsSaving(false);
    }
  }

  const isAdmin = role === "ADMIN";

  return (
    <SettingsPageFrame
      badge={<span className="page-badge"><ShieldCheck size={16} /> {isAdmin ? "관리자" : "멤버"}</span>}
      description="공유 장부에서 사용할 식비 분류와 표시 순서를 관리하세요."
      eyebrow="CATEGORY SETTINGS"
      showBackLink
      title="카테고리 관리"
    >
      {error && <div className="message-banner message-banner--error" role="alert">{error}</div>}
      {notice && (
        <button className="message-banner message-banner--notice" onClick={() => setNotice(null)} type="button">
          {notice}<X size={16} />
        </button>
      )}

      {isLoading ? (
        <section className="page-loading"><LoaderCircle className="spin" size={28} /><strong>카테고리를 불러오고 있어요</strong></section>
      ) : (
        <section className="category-settings-panel" aria-labelledby="category-list-title">
          <div className="category-settings-toolbar">
            <div>
              <p className="eyebrow">CATEGORY LIST</p>
              <h2 id="category-list-title">사용 중인 카테고리</h2>
              <p>정렬 순서가 작은 항목부터 식비 등록 화면에 표시됩니다.</p>
            </div>
            {isAdmin && <button className="dark-button" onClick={openCreate} type="button"><Plus size={17} /> 새 카테고리 추가</button>}
          </div>

          {!isAdmin && <p className="category-permission-note"><ShieldCheck size={17} /> 카테고리 변경은 관리자만 할 수 있습니다.</p>}

          {draft && (
            <form className="category-editor" onSubmit={saveCategory}>
              <div className="category-editor-heading">
                <span><FolderCog size={20} /></span>
                <div><p className="eyebrow">{draft.id ? "EDIT CATEGORY" : "NEW CATEGORY"}</p><h3>{draft.id ? "카테고리 수정" : "새 카테고리 추가"}</h3></div>
              </div>
              <div className="category-editor-fields">
                <label><span>카테고리 이름</span><input autoFocus maxLength={50} onChange={(event) => setDraft({ ...draft, name: event.target.value })} placeholder="예: 회사 점심" required value={draft.name} /></label>
                <label><span>정렬 순서</span><input min={1} onChange={(event) => setDraft({ ...draft, sortOrder: event.target.value })} required type="number" value={draft.sortOrder} /></label>
              </div>
              <div className="category-editor-actions">
                <button className="secondary-button" disabled={isSaving} onClick={() => setDraft(null)} type="button">취소</button>
                <button className="dark-button" disabled={isSaving} type="submit">{isSaving && <LoaderCircle className="spin" size={16} />}{draft.id ? "변경사항 저장" : "카테고리 추가"}</button>
              </div>
            </form>
          )}

          {categories.length === 0 ? (
            <div className="category-settings-empty"><Tags size={24} /><strong>등록된 카테고리가 없습니다.</strong>{isAdmin && <span>새 카테고리를 추가해 주세요.</span>}</div>
          ) : (
            <ol className="category-settings-list">
              {categories.map((category) => (
                <li key={category.id}>
                  <span className="category-settings-order">{String(category.sortOrder).padStart(2, "0")}</span>
                  <span className="category-settings-name"><strong>{category.name}</strong><small>정렬 순서 {category.sortOrder}</small></span>
                  {isAdmin && (
                    <span className="category-settings-actions">
                      <button aria-label={`${category.name} 수정`} onClick={() => openEdit(category)} type="button"><Pencil size={16} /></button>
                      <button aria-label={`${category.name} 삭제`} className="is-danger" onClick={() => setDeleteTarget(category)} type="button"><Trash2 size={16} /></button>
                    </span>
                  )}
                </li>
              ))}
            </ol>
          )}
        </section>
      )}

      {deleteTarget && (
        <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && !isSaving) setDeleteTarget(null); }} role="presentation">
          <section aria-labelledby="category-delete-title" aria-modal="true" className="category-delete-dialog" role="dialog">
            <span className="category-delete-icon"><CircleAlert size={26} /></span>
            <p className="eyebrow">DELETE CATEGORY</p>
            <h2 id="category-delete-title">{deleteTarget.name} 카테고리를 삭제할까요?</h2>
            <p>삭제 후 되돌릴 수 없습니다. 기존 식비 내역은 삭제되지 않고 카테고리만 ‘분류 없음’으로 변경됩니다.</p>
            <div>
              <button className="secondary-button" disabled={isSaving} onClick={() => setDeleteTarget(null)} type="button">취소</button>
              <button className="dark-button category-delete-button" disabled={isSaving} onClick={() => void deleteCategory()} type="button">{isSaving && <LoaderCircle className="spin" size={16} />}삭제</button>
            </div>
          </section>
        </div>
      )}
    </SettingsPageFrame>
  );
}
