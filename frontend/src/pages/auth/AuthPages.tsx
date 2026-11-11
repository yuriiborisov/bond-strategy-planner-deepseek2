import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuthStore } from "../../app/store";
import { api } from "../../shared/api/endpoints";
import { Button, Card, Field, TextInput } from "../../shared/ui";

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => api.login(email.trim(), password),
    onSuccess: (tokens) => {
      setAuth(tokens);
      navigate(params.get("next") ?? "/portfolios", { replace: true });
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <div className="mx-auto max-w-md py-6">
      <Card className="card-pad">
        <h1 className="text-xl font-semibold text-white">Вход</h1>
        <p className="mt-1 text-sm text-slate-400">Войдите, чтобы сохранять портфели и запускать AI-разбор.</p>
        <form
          className="mt-5 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            setError(null);
            mutation.mutate();
          }}
        >
          <Field label="Email" htmlFor="email">
            <TextInput
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
            />
          </Field>
          <Field label="Пароль" htmlFor="password">
            <TextInput
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
            />
          </Field>
          {error ? <p className="text-sm text-rose-300">{error}</p> : null}
          <Button type="submit" disabled={mutation.isPending} className="w-full">
            {mutation.isPending ? "Входим…" : "Войти"}
          </Button>
        </form>
        <p className="mt-4 text-sm text-slate-400">
          Нет аккаунта?{" "}
          <Link to="/register" className="font-medium">
            Зарегистрироваться
          </Link>
        </p>
        <p className="mt-4 rounded-xl border border-white/10 bg-white/[0.02] px-3 py-2 text-xs text-slate-500">
          Демо-доступ (если включён APP_SEED_DEMO): admin@local.dev / admin12345 — роль ADMIN.
        </p>
      </Card>
    </div>
  );
}

export function RegisterPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => api.register(email.trim(), password),
    onSuccess: (tokens) => {
      setAuth(tokens);
      navigate("/portfolios", { replace: true });
    },
    onError: (err: Error) => setError(err.message),
  });

  return (
    <div className="mx-auto max-w-md py-6">
      <Card className="card-pad">
        <h1 className="text-xl font-semibold text-white">Регистрация</h1>
        <p className="mt-1 text-sm text-slate-400">
          Аккаунт нужен для сохранения портфелей, истории и AI-разбора. Пароль хранится только в виде хэша.
        </p>
        <form
          className="mt-5 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            setError(null);
            mutation.mutate();
          }}
        >
          <Field label="Email" htmlFor="email">
            <TextInput
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
            />
          </Field>
          <Field label="Пароль" hint="Минимум 8 символов" htmlFor="password">
            <TextInput
              id="password"
              type="password"
              autoComplete="new-password"
              minLength={8}
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="••••••••"
            />
          </Field>
          {error ? <p className="text-sm text-rose-300">{error}</p> : null}
          <Button type="submit" disabled={mutation.isPending} className="w-full">
            {mutation.isPending ? "Создаём…" : "Создать аккаунт"}
          </Button>
        </form>
        <p className="mt-4 text-sm text-slate-400">
          Уже есть аккаунт?{" "}
          <Link to="/login" className="font-medium">
            Войти
          </Link>
        </p>
      </Card>
    </div>
  );
}
