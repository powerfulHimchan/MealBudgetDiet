import {
  BarChart3,
  ChevronRight,
  Coffee,
  House,
  Plus,
  ReceiptText,
  Settings,
  ShoppingBasket,
  Utensils,
} from "lucide-react";
import Link from "next/link";

const recentExpenses = [
  { category: "장보기", merchant: "동네마트", date: "오늘", amount: 18_500, icon: ShoppingBasket, tone: "mint" },
  { category: "외식", merchant: "을지로 식당", date: "어제", amount: 32_000, icon: Utensils, tone: "orange" },
  { category: "카페/간식", merchant: "커피하우스", date: "9월 6일", amount: 9_800, icon: Coffee, tone: "blue" },
];

const won = new Intl.NumberFormat("ko-KR");

export default function Home() {
  return (
    <main className="app-shell">
      <header className="topbar">
        <Link className="brand" href="/" aria-label="MealBudgetDiet 홈">
          <span className="brand-mark">M</span>
          <span>MealBudgetDiet</span>
        </Link>
        <button className="avatar" type="button" aria-label="계정 설정">힘</button>
      </header>

      <div className="dashboard-grid">
        <section className="budget-card" aria-labelledby="budget-title">
          <div className="budget-card__topline">
            <div>
              <p className="eyebrow">9월 예산</p>
              <h1 id="budget-title">141,200원 남았어요</h1>
            </div>
            <span className="status-chip">주의</span>
          </div>

          <div className="progress-track" aria-label="예산 사용률 82%">
            <span style={{ width: "82%" }} />
          </div>

          <div className="budget-stats">
            <div><span>이번 달 사용</span><strong>{won.format(658_800)}원</strong></div>
            <div><span>전체 예산</span><strong>{won.format(800_000)}원</strong></div>
            <div className="usage-stat"><span>사용률</span><strong>82%</strong></div>
          </div>

          <button className="primary-action" type="button">
            <Plus size={20} strokeWidth={2.5} />
            식비 등록
          </button>
        </section>

        <section className="recent-card" aria-labelledby="recent-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">최근 기록</p>
              <h2 id="recent-title">새로 등록된 식비</h2>
            </div>
            <Link href="/expenses">전체 보기<ChevronRight size={17} /></Link>
          </div>

          <ul className="expense-list">
            {recentExpenses.map((expense) => {
              const Icon = expense.icon;
              return (
                <li key={`${expense.date}-${expense.merchant}`}>
                  <span className={`expense-icon expense-icon--${expense.tone}`}><Icon size={20} /></span>
                  <div className="expense-copy">
                    <strong>{expense.merchant}</strong>
                    <span>{expense.category} · {expense.date}</span>
                  </div>
                  <strong className="expense-amount">-{won.format(expense.amount)}원</strong>
                </li>
              );
            })}
          </ul>
        </section>
      </div>

      <nav className="bottom-nav" aria-label="주요 메뉴">
        <Link className="is-active" href="/"><House size={20} /><span>홈</span></Link>
        <Link href="/expenses"><ReceiptText size={20} /><span>식비</span></Link>
        <Link href="/statistics"><BarChart3 size={20} /><span>통계</span></Link>
        <Link href="/settings"><Settings size={20} /><span>설정</span></Link>
      </nav>
    </main>
  );
}
