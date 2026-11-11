const messages = {
  appName: "Bond Strategy Planner",
  appTagline: "Подбор, расчёт и моделирование портфелей облигаций",
  disclaimer:
    "Сервис носит информационный характер и не является индивидуальной инвестиционной рекомендацией. Проверяйте первоисточники.",
  aiDisclaimer:
    "Сгенерировано AI на основе доступных данных. Не является индивидуальной инвестиционной рекомендацией. Проверяйте первоисточники.",
  nav: {
    strategies: "Стратегии",
    portfolios: "Портфели",
    admin: "Админка",
    login: "Войти",
    register: "Регистрация",
    logout: "Выйти",
  },
  common: {
    calculate: "Рассчитать",
    calculating: "Считаем…",
    save: "Сохранить",
    cancel: "Отмена",
    delete: "Удалить",
    exportCsv: "Экспорт CSV",
    exportXlsx: "Экспорт XLSX",
    retry: "Повторить",
    back: "Назад",
    loading: "Загрузка…",
  },
} as const;

type Leaves<T> = T extends string ? T : { [K in keyof T]: Leaves<T[K]> }[keyof T];

export type MessageKey = Leaves<typeof messages>;

export function t(key: MessageKey): string {
  return key;
}

export function getMessage(path: string): string {
  return path.split(".").reduce<unknown>((acc, part) => {
    if (acc && typeof acc === "object") {
      return (acc as Record<string, unknown>)[part];
    }
    return undefined;
  }, messages) as string ?? path;
}

export default messages;
