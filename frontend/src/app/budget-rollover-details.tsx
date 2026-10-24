export type BudgetRollover = {
  enabled: boolean;
  baseAmount: number;
  carryAmount: number;
  effectiveAmount: number;
};

const won = new Intl.NumberFormat("ko-KR");

export function BudgetRolloverDetails({ value, range = false }: { value?: BudgetRollover | null; range?: boolean }) {
  if (!value?.enabled) return null;
  return (
    <div className="budget-rollover-details" aria-label="예산 이월 내역">
      {!range && <span>기본 예산 <b>{won.format(value.baseAmount)}원</b></span>}
      <span>{range ? "조회 시작 주기 이월" : "직전 주기 이월"} <b>{value.carryAmount > 0 ? "+" : ""}{won.format(value.carryAmount)}원</b></span>
      {!range && value.effectiveAmount <= 0 && <span>사용 가능 예산이 없어요.{value.effectiveAmount < 0 && ` 초과 이월금 ${won.format(-value.effectiveAmount)}원이 남아 있어요.`}</span>}
      {range && <small>주기 사이에 넘긴 금액은 중복 합산하지 않아요.</small>}
    </div>
  );
}
