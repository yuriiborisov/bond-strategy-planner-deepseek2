import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuthStore } from "../../app/store";
import { api } from "../../shared/api/endpoints";
import { Button, Field, Modal, TextInput } from "../../shared/ui";

export function SavePortfolioDialog({
  open,
  calculationId,
  defaultName,
  onClose,
  onSaved,
}: {
  open: boolean;
  calculationId: number;
  defaultName: string;
  onClose: () => void;
  onSaved: (portfolio: { id: number; name: string }) => void;
}) {
  const user = useAuthStore((state) => state.user);
  const location = useLocation();
  const [name, setName] = useState(defaultName);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => api.saveCalculation(calculationId, name.trim() || defaultName),
    onSuccess: (portfolio) => {
      setError(null);
      onSaved({ id: portfolio.id, name: portfolio.name });
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <Modal
      open={open}
      title="Сохранить портфель"
      onClose={onClose}
      footer={
        user ? (
          <>
            <Button variant="ghost" onClick={onClose}>
              Отмена
            </Button>
            <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
              {mutation.isPending ? "Сохраняем…" : "Сохранить"}
            </Button>
          </>
        ) : null
      }
    >
      {user ? (
        <div className="space-y-4">
          <Field label="Название портфеля" htmlFor="portfolio-name">
            <TextInput
              id="portfolio-name"
              value={name}
              maxLength={120}
              onChange={(event) => setName(event.target.value)}
              placeholder="Например, «Цель: квартира 2031»"
            />
          </Field>
          <p className="text-sm text-slate-400">
            Расчёт сохранится вместе со снимком данных и параметрами. Позже портфель можно открыть, пересчитать на
            актуальных данных, экспортировать в CSV/XLSX и запросить AI-разбор.
          </p>
          {error ? <p className="text-sm text-rose-300">{error}</p> : null}
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-slate-300">
            Чтобы сохранять портфели в историю, войдите или зарегистрируйтесь. Расчёт при этом уже сохранён анонимно и
            доступен по ссылке в этой сессии.
          </p>
          <div className="flex gap-2">
            <Link className="btn-primary" to={`/login?next=${encodeURIComponent(location.pathname)}`}>
              Войти
            </Link>
            <Link className="btn-secondary" to="/register">
              Регистрация
            </Link>
          </div>
        </div>
      )}
    </Modal>
  );
}
