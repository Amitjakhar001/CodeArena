# CodeArena — Next.js 15 Frontend Specification
*Component-by-component spec for the frontend. Paste this into Claude Code when implementing Phase 6 — it has every page, every component, every API call shape.*

---

## 1. Stack & Constraints

- **Framework:** Next.js 15 (App Router, Server Components where possible, Client Components for interactive pieces)
- **Language:** TypeScript (strict mode, no `any` without justification)
- **Styling:** Tailwind CSS 4
- **Code Editor:** `@monaco-editor/react`
- **Icons:** `lucide-react`
- **HTTP:** native `fetch` with a custom wrapper at `lib/api.ts` (no axios — overkill for our needs)
- **State management:** React Context for auth only. No Redux, no Zustand, no Jotai. If you find yourself needing global state for non-auth data, you're overengineering.
- **Forms:** native React state for our form complexity. No react-hook-form or Formik.
- **No UI library** (no shadcn, no Material, no Chakra). We hand-build with Tailwind. Reasons:
  1. Tailwind 4's design tokens are enough
  2. Demonstrates "I can write CSS" not "I can install a library"
  3. Bundle size stays small

**Why not server actions for the API calls:** We have a real backend (Spring Boot through API Gateway), not Next.js as the backend. Server actions would just be a thin proxy — direct client `fetch` to the gateway is simpler and more honest about the architecture.

---

## 2. Directory Structure

```
frontend/
├── app/
│   ├── layout.tsx                    # Root layout with AuthProvider, global styles
│   ├── page.tsx                      # Landing — problem list (server component)
│   ├── globals.css                   # Tailwind directives + custom CSS variables
│   ├── login/
│   │   └── page.tsx                  # Login form (client)
│   ├── register/
│   │   └── page.tsx                  # Register form (client)
│   ├── playground/
│   │   └── page.tsx                  # Free-form code execution (client)
│   ├── problems/
│   │   ├── page.tsx                  # Problem list (server)
│   │   └── [slug]/
│   │       └── page.tsx              # Problem detail with editor (client)
│   ├── submissions/
│   │   ├── page.tsx                  # History list (client)
│   │   └── [id]/
│   │       └── page.tsx              # Single submission detail (client)
│   └── me/
│       └── stats/
│           └── page.tsx              # User stats dashboard (client)
│
├── components/
│   ├── CodeEditor.tsx                # Monaco wrapper
│   ├── LanguageSelector.tsx          # Dropdown
│   ├── ExecutionOutput.tsx           # stdout/stderr/compile panels
│   ├── SubmissionStatusBadge.tsx     # Colored badge
│   ├── StdinInput.tsx                # Collapsible textarea
│   ├── Navbar.tsx                    # Top nav with auth state
│   ├── ProtectedRoute.tsx            # HOC for client pages requiring auth
│   ├── ProblemCard.tsx               # Used in lists
│   └── StatsCard.tsx                 # Stats dashboard cards
│
├── lib/
│   ├── api.ts                        # API client wrapping all backend calls
│   ├── auth-context.tsx              # AuthProvider + useAuth hook
│   ├── types.ts                      # Shared TypeScript types
│   ├── constants.ts                  # Status colors, language defaults, etc.
│   └── hooks/
│       ├── usePollSubmission.ts      # Polls until status != PENDING
│       └── useLanguages.ts           # Fetches and caches language list
│
├── middleware.ts                     # Protected route enforcement
├── next.config.js
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── Dockerfile
```

---

## 3. TypeScript Types (`lib/types.ts`)

```typescript
export type UserRole = 'USER' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  username: string;
  role: UserRole;
  createdAt: string;
}

export interface AuthResponse {
  user: User;
  accessToken: string;
  refreshToken: string;
}

export type ExecutionStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'COMPILATION_ERROR'
  | 'RUNTIME_ERROR'
  | 'INTERNAL_ERROR';

export interface ExecutionResult {
  executionToken: string;
  status: ExecutionStatus;
  statusDescription: string;
  stdout: string | null;
  stderr: string | null;
  compileOutput: string | null;
  timeMs: number | null;
  memoryKb: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface Submission {
  id: string;
  userId: string;
  problemId: string | null;
  languageId: number;
  sourceCode: string;
  customStdin: string | null;
  latestStatus: ExecutionStatus;
  executions: ExecutionResult[];
  createdAt: string;
  updatedAt: string;
}

export interface SubmissionListItem {
  id: string;
  problemId: string | null;
  problemTitle: string | null;
  languageId: number;
  latestStatus: ExecutionStatus;
  createdAt: string;
}

export interface Problem {
  id: string;
  slug: string;
  title: string;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  description?: string;
  sampleTestCases?: SampleTestCase[];
  supportedLanguages: number[];
}

export interface SampleTestCase {
  input: string;
  expectedOutput: string;
  isHidden: boolean;
}

export interface Language {
  id: number;
  name: string;
}

export interface UserStats {
  totalSubmissions: number;
  acceptedSubmissions: number;
  byLanguage: Record<string, LanguageStats>;
  lastSubmissionAt: string | null;
}

export interface LanguageStats {
  count: number;
  accepted: number;
  avgTimeMs: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  total: number;
}

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  correlationId?: string;
  errors?: Array<{ field: string; message: string }>;
}
```

---

## 4. API Client (`lib/api.ts`)

The single most important file in the frontend. All backend communication goes through here.

```typescript
import type { 
  AuthResponse, User, Problem, Submission, SubmissionListItem,
  Language, UserStats, PaginatedResponse, ApiError, ExecutionStatus
} from './types';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080/api/v1';

class ApiClientImpl {
  private accessToken: string | null = null;
  private refreshPromise: Promise<string> | null = null;

  setAccessToken(token: string | null) {
    this.accessToken = token;
  }

  getAccessToken() {
    return this.accessToken;
  }

  private async request<T>(
    path: string,
    options: RequestInit = {},
    isRetry = false
  ): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...((options.headers as Record<string, string>) || {})
    };

    if (this.accessToken) {
      headers['Authorization'] = `Bearer ${this.accessToken}`;
    }

    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers,
      credentials: 'include' // for httpOnly refresh token cookie
    });

    // Handle 401 with one refresh attempt
    if (res.status === 401 && !isRetry && path !== '/auth/refresh') {
      try {
        await this.refreshAccessToken();
        return this.request<T>(path, options, true);
      } catch {
        // Refresh failed; bubble up the original 401
        this.accessToken = null;
        if (typeof window !== 'undefined') {
          window.location.href = '/login';
        }
        throw await this.parseError(res);
      }
    }

    if (!res.ok) {
      throw await this.parseError(res);
    }

    if (res.status === 204) return undefined as T;
    return res.json() as Promise<T>;
  }

  private async parseError(res: Response): Promise<ApiError> {
    try {
      return await res.json();
    } catch {
      return {
        type: 'about:blank',
        title: 'Unknown error',
        status: res.status,
        detail: res.statusText
      };
    }
  }

  private async refreshAccessToken(): Promise<string> {
    // Deduplicate concurrent refresh attempts
    if (this.refreshPromise) return this.refreshPromise;

    this.refreshPromise = (async () => {
      const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        credentials: 'include'
      });
      if (!res.ok) throw new Error('Refresh failed');
      const data: { accessToken: string } = await res.json();
      this.accessToken = data.accessToken;
      return data.accessToken;
    })();

    try {
      return await this.refreshPromise;
    } finally {
      this.refreshPromise = null;
    }
  }

  // ─── Auth ────────────────────────────────────────────────────────
  async register(email: string, username: string, password: string): Promise<AuthResponse> {
    return this.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, username, password })
    });
  }

  async login(emailOrUsername: string, password: string): Promise<AuthResponse> {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ emailOrUsername, password })
    });
  }

  async logout(refreshToken: string): Promise<void> {
    return this.request('/auth/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken })
    });
  }

  async me(): Promise<{ user: User }> {
    return this.request('/auth/me');
  }

  // ─── Problems ────────────────────────────────────────────────────
  async listProblems(): Promise<Problem[]> {
    return this.request('/problems');
  }

  async getProblem(slug: string): Promise<Problem> {
    return this.request(`/problems/${slug}`);
  }

  // ─── Languages ───────────────────────────────────────────────────
  async listLanguages(): Promise<Language[]> {
    return this.request('/languages');
  }

  // ─── Submissions ─────────────────────────────────────────────────
  async submitCode(input: {
    problemId: string | null;
    languageId: number;
    sourceCode: string;
    customStdin: string | null;
  }): Promise<{ submissionId: string; status: ExecutionStatus }> {
    return this.request('/submissions', {
      method: 'POST',
      body: JSON.stringify(input)
    });
  }

  async getSubmission(id: string): Promise<Submission> {
    return this.request(`/submissions/${id}`);
  }

  async listSubmissions(params: { page?: number; size?: number; problemId?: string }): Promise<PaginatedResponse<SubmissionListItem>> {
    const query = new URLSearchParams();
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    if (params.problemId) query.set('problemId', params.problemId);
    return this.request(`/submissions?${query.toString()}`);
  }

  // ─── Stats ───────────────────────────────────────────────────────
  async getMyStats(): Promise<UserStats> {
    return this.request('/users/me/stats');
  }
}

export const api = new ApiClientImpl();
```

**Why this shape:**
- One singleton, holds access token in memory (not localStorage — XSS resilience)
- Automatic 401 → refresh → retry, with deduplication (no 5 simultaneous refresh calls if 5 tabs open)
- Refresh token in httpOnly cookie set by the backend, sent automatically via `credentials: 'include'`
- Errors are typed (`ApiError` matches our backend's RFC 7807 shape)
- No axios — `fetch` does everything we need

---

## 5. Auth Context (`lib/auth-context.tsx`)

```typescript
'use client';

import { createContext, useContext, useEffect, useState, useCallback, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { api } from './api';
import type { User, AuthResponse } from './types';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (emailOrUsername: string, password: string) => Promise<void>;
  register: (email: string, username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  // Bootstrap: try to refresh + load user on mount
  useEffect(() => {
    (async () => {
      try {
        // Hits /auth/refresh which uses the httpOnly cookie
        // If valid, we get a new access token; then fetch /me
        await fetch(`${process.env.NEXT_PUBLIC_API_BASE}/auth/refresh`, {
          method: 'POST',
          credentials: 'include'
        }).then(async (res) => {
          if (res.ok) {
            const { accessToken } = await res.json();
            api.setAccessToken(accessToken);
            const { user } = await api.me();
            setUser(user);
          }
        });
      } catch {
        // Not logged in; that's fine
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleAuthSuccess = useCallback((res: AuthResponse) => {
    api.setAccessToken(res.accessToken);
    setUser(res.user);
    // Refresh token is set as httpOnly cookie by the server
  }, []);

  const login = useCallback(async (emailOrUsername: string, password: string) => {
    const res = await api.login(emailOrUsername, password);
    handleAuthSuccess(res);
  }, [handleAuthSuccess]);

  const register = useCallback(async (email: string, username: string, password: string) => {
    const res = await api.register(email, username, password);
    handleAuthSuccess(res);
  }, [handleAuthSuccess]);

  const logout = useCallback(async () => {
    try {
      // Server clears the cookie
      await fetch(`${process.env.NEXT_PUBLIC_API_BASE}/auth/logout`, {
        method: 'POST',
        credentials: 'include'
      });
    } finally {
      api.setAccessToken(null);
      setUser(null);
      router.push('/login');
    }
  }, [router]);

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
```

---

## 6. The Polling Hook (`lib/hooks/usePollSubmission.ts`)

```typescript
'use client';

import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Submission } from '../types';

export function usePollSubmission(submissionId: string | null) {
  const [submission, setSubmission] = useState<Submission | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!submissionId) return;

    let cancelled = false;
    let timeoutId: ReturnType<typeof setTimeout>;
    let attempts = 0;
    const MAX_ATTEMPTS = 30; // 30 seconds at 1s interval

    const poll = async () => {
      if (cancelled) return;
      try {
        setLoading(true);
        const data = await api.getSubmission(submissionId);
        if (cancelled) return;
        setSubmission(data);
        setError(null);

        if (data.latestStatus === 'PENDING' && attempts < MAX_ATTEMPTS) {
          attempts++;
          timeoutId = setTimeout(poll, 1000);
        } else {
          setLoading(false);
        }
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : 'Failed to fetch submission');
        setLoading(false);
      }
    };

    poll();

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [submissionId]);

  return { submission, loading, error };
}
```

---

## 7. Component Specifications

### 7.1 `<CodeEditor>` (`components/CodeEditor.tsx`)

```typescript
'use client';

import Editor from '@monaco-editor/react';
import { LANGUAGE_ID_TO_MONACO } from '@/lib/constants';

interface Props {
  value: string;
  onChange: (value: string) => void;
  languageId: number;
  height?: string;
  readOnly?: boolean;
}

export function CodeEditor({ value, onChange, languageId, height = '500px', readOnly = false }: Props) {
  const monacoLang = LANGUAGE_ID_TO_MONACO[languageId] || 'plaintext';
  
  return (
    <div className="rounded-lg border border-slate-700 overflow-hidden">
      <Editor
        height={height}
        language={monacoLang}
        value={value}
        onChange={(v) => onChange(v || '')}
        theme="vs-dark"
        options={{
          minimap: { enabled: false },
          fontSize: 14,
          fontFamily: 'Menlo, Monaco, "Courier New", monospace',
          tabSize: 4,
          insertSpaces: true,
          wordWrap: 'on',
          scrollBeyondLastLine: false,
          readOnly,
          automaticLayout: true
        }}
      />
    </div>
  );
}
```

### 7.2 `<LanguageSelector>` (`components/LanguageSelector.tsx`)

```typescript
'use client';

import { useLanguages } from '@/lib/hooks/useLanguages';

interface Props {
  value: number;
  onChange: (id: number) => void;
  allowedIds?: number[]; // restrict to problem's supportedLanguages
}

export function LanguageSelector({ value, onChange, allowedIds }: Props) {
  const { languages, loading } = useLanguages();

  if (loading) {
    return <div className="h-10 w-48 bg-slate-800 animate-pulse rounded" />;
  }

  const filtered = allowedIds
    ? languages.filter(l => allowedIds.includes(l.id))
    : languages;

  return (
    <select
      value={value}
      onChange={(e) => onChange(Number(e.target.value))}
      className="bg-slate-800 text-slate-100 border border-slate-700 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
    >
      {filtered.map(lang => (
        <option key={lang.id} value={lang.id}>{lang.name}</option>
      ))}
    </select>
  );
}
```

### 7.3 `<ExecutionOutput>` (`components/ExecutionOutput.tsx`)

```typescript
'use client';

import { useState } from 'react';
import type { ExecutionResult } from '@/lib/types';
import { SubmissionStatusBadge } from './SubmissionStatusBadge';

interface Props {
  execution: ExecutionResult | null;
  loading: boolean;
}

export function ExecutionOutput({ execution, loading }: Props) {
  const [tab, setTab] = useState<'stdout' | 'stderr' | 'compile'>('stdout');

  if (loading && !execution) {
    return (
      <div className="rounded-lg border border-slate-700 p-6 text-center text-slate-400">
        <div className="animate-spin h-6 w-6 border-2 border-slate-600 border-t-blue-500 rounded-full mx-auto mb-3" />
        Executing your code...
      </div>
    );
  }

  if (!execution) {
    return (
      <div className="rounded-lg border border-slate-700 p-6 text-center text-slate-500">
        Run your code to see output here.
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-700 overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 bg-slate-800 border-b border-slate-700">
        <SubmissionStatusBadge status={execution.status} />
        <div className="flex gap-4 text-xs text-slate-400">
          {execution.timeMs !== null && <span>{execution.timeMs}ms</span>}
          {execution.memoryKb !== null && <span>{(execution.memoryKb / 1024).toFixed(1)}MB</span>}
        </div>
      </div>

      <div className="flex border-b border-slate-700 bg-slate-900">
        <TabButton active={tab === 'stdout'} onClick={() => setTab('stdout')}>stdout</TabButton>
        <TabButton active={tab === 'stderr'} onClick={() => setTab('stderr')}>stderr</TabButton>
        <TabButton active={tab === 'compile'} onClick={() => setTab('compile')}>compile</TabButton>
      </div>

      <pre className="p-4 bg-slate-950 text-slate-200 text-sm font-mono overflow-x-auto min-h-[200px] whitespace-pre-wrap">
        {tab === 'stdout' && (execution.stdout || <span className="text-slate-600">(empty)</span>)}
        {tab === 'stderr' && (execution.stderr || <span className="text-slate-600">(empty)</span>)}
        {tab === 'compile' && (execution.compileOutput || <span className="text-slate-600">(empty)</span>)}
      </pre>
    </div>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 text-sm font-mono transition-colors ${
        active 
          ? 'text-blue-400 border-b-2 border-blue-500 -mb-px' 
          : 'text-slate-400 hover:text-slate-200'
      }`}
    >
      {children}
    </button>
  );
}
```

### 7.4 `<SubmissionStatusBadge>` (`components/SubmissionStatusBadge.tsx`)

```typescript
import type { ExecutionStatus } from '@/lib/types';

const STATUS_STYLES: Record<ExecutionStatus, { label: string; className: string }> = {
  PENDING:                { label: 'Pending',        className: 'bg-slate-700 text-slate-200' },
  ACCEPTED:               { label: 'Accepted',       className: 'bg-emerald-900/50 text-emerald-300 border border-emerald-700' },
  WRONG_ANSWER:           { label: 'Wrong Answer',   className: 'bg-rose-900/50 text-rose-300 border border-rose-700' },
  TIME_LIMIT_EXCEEDED:    { label: 'Time Limit',     className: 'bg-amber-900/50 text-amber-300 border border-amber-700' },
  MEMORY_LIMIT_EXCEEDED:  { label: 'Memory Limit',   className: 'bg-amber-900/50 text-amber-300 border border-amber-700' },
  COMPILATION_ERROR:      { label: 'Compile Error',  className: 'bg-orange-900/50 text-orange-300 border border-orange-700' },
  RUNTIME_ERROR:          { label: 'Runtime Error',  className: 'bg-rose-900/50 text-rose-300 border border-rose-700' },
  INTERNAL_ERROR:         { label: 'Internal Error', className: 'bg-purple-900/50 text-purple-300 border border-purple-700' }
};

export function SubmissionStatusBadge({ status }: { status: ExecutionStatus }) {
  const { label, className } = STATUS_STYLES[status];
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
```

### 7.5 `<StdinInput>` (`components/StdinInput.tsx`)

```typescript
'use client';

import { useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';

interface Props {
  value: string;
  onChange: (value: string) => void;
}

export function StdinInput({ value, onChange }: Props) {
  const [open, setOpen] = useState(false);

  return (
    <div className="rounded-lg border border-slate-700 overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 w-full px-4 py-2 bg-slate-800 hover:bg-slate-750 text-sm text-slate-300 text-left"
      >
        {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        Custom Input (stdin)
      </button>
      {open && (
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Enter input that will be piped to your program's stdin..."
          className="w-full bg-slate-950 text-slate-200 text-sm font-mono p-3 min-h-[100px] focus:outline-none border-t border-slate-700"
        />
      )}
    </div>
  );
}
```

### 7.6 `<Navbar>` (`components/Navbar.tsx`)

```typescript
'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth-context';
import { Code2, LogOut, User as UserIcon } from 'lucide-react';

export function Navbar() {
  const { user, logout } = useAuth();

  return (
    <nav className="border-b border-slate-800 bg-slate-950">
      <div className="max-w-7xl mx-auto px-4 h-14 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2 text-slate-100 font-semibold">
          <Code2 size={20} className="text-blue-400" />
          CodeArena
        </Link>

        <div className="flex items-center gap-6 text-sm">
          <Link href="/problems" className="text-slate-300 hover:text-white">Problems</Link>
          <Link href="/playground" className="text-slate-300 hover:text-white">Playground</Link>
          {user && (
            <>
              <Link href="/submissions" className="text-slate-300 hover:text-white">History</Link>
              <Link href="/me/stats" className="text-slate-300 hover:text-white">Stats</Link>
            </>
          )}

          {user ? (
            <div className="flex items-center gap-3 pl-6 border-l border-slate-800">
              <div className="flex items-center gap-2 text-slate-300">
                <UserIcon size={14} />
                <span>{user.username}</span>
              </div>
              <button onClick={logout} className="text-slate-400 hover:text-rose-400">
                <LogOut size={14} />
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-3 pl-6 border-l border-slate-800">
              <Link href="/login" className="text-slate-300 hover:text-white">Login</Link>
              <Link href="/register" className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded text-sm">
                Sign Up
              </Link>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
}
```

---

## 8. Key Pages

### 8.1 Playground (`app/playground/page.tsx`) — the centerpiece

```typescript
'use client';

import { useState } from 'react';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { CodeEditor } from '@/components/CodeEditor';
import { LanguageSelector } from '@/components/LanguageSelector';
import { StdinInput } from '@/components/StdinInput';
import { ExecutionOutput } from '@/components/ExecutionOutput';
import { usePollSubmission } from '@/lib/hooks/usePollSubmission';
import { api } from '@/lib/api';
import { Play } from 'lucide-react';

export default function PlaygroundPage() {
  const [languageId, setLanguageId] = useState(71); // Python
  const [sourceCode, setSourceCode] = useState('print("Hello, CodeArena!")\n');
  const [stdin, setStdin] = useState('');
  const [submissionId, setSubmissionId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { submission, loading } = usePollSubmission(submissionId);
  const latestExecution = submission?.executions?.[submission.executions.length - 1] ?? null;

  const handleRun = async () => {
    setSubmitError(null);
    setSubmitting(true);
    try {
      const { submissionId } = await api.submitCode({
        problemId: null,
        languageId,
        sourceCode,
        customStdin: stdin || null
      });
      setSubmissionId(submissionId);
    } catch (e: any) {
      setSubmitError(e?.detail || 'Submission failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h1 className="text-2xl font-semibold text-white">Playground</h1>
            <p className="text-sm text-slate-400 mt-1">Run code in any supported language. No problem context.</p>
          </div>
          <div className="flex items-center gap-3">
            <LanguageSelector value={languageId} onChange={setLanguageId} />
            <button
              onClick={handleRun}
              disabled={submitting || loading}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 disabled:text-slate-400 text-white rounded text-sm font-medium"
            >
              <Play size={14} fill="currentColor" />
              {submitting ? 'Submitting...' : loading ? 'Running...' : 'Run'}
            </button>
          </div>
        </div>

        {submitError && (
          <div className="mb-4 px-4 py-3 bg-rose-900/30 border border-rose-800 rounded text-sm text-rose-200">
            {submitError}
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="space-y-4">
            <CodeEditor value={sourceCode} onChange={setSourceCode} languageId={languageId} />
            <StdinInput value={stdin} onChange={setStdin} />
          </div>
          <ExecutionOutput execution={latestExecution} loading={loading} />
        </div>
      </div>
    </ProtectedRoute>
  );
}
```

### 8.2 Other pages — outline

I'm not writing every page in full because the patterns repeat. Here's what each does:

- **`/login`** — form with `emailOrUsername` + `password`, calls `auth.login`, redirects to `/playground` on success
- **`/register`** — form with email + username + password + confirmPassword, calls `auth.register`, redirects to `/playground`
- **`/`** — server component, renders welcome + problem list teaser
- **`/problems`** — server component, fetches and renders problem cards in a grid
- **`/problems/[slug]`** — client component, similar to playground but with problem description on the left side and editor on the right; submits with `problemId`
- **`/submissions`** — client component, paginated list of `SubmissionListItem`, click row → `/submissions/[id]`
- **`/submissions/[id]`** — client component, full submission detail: source code in read-only Monaco, execution history (one submission can have multiple executions), latest execution output
- **`/me/stats`** — client component, `<StatsCard>` grid showing total / accepted / by-language breakdown

For each, the pattern is:
1. Wrap in `<ProtectedRoute>` if authenticated
2. Fetch via `api.X()` in `useEffect`
3. Render with the components above
4. Handle loading and error states

---

## 9. Middleware (`middleware.ts`)

```typescript
import { NextRequest, NextResponse } from 'next/server';

const PROTECTED_PATHS = ['/playground', '/submissions', '/me'];

export function middleware(request: NextRequest) {
  // We can't reliably check JWT in middleware (it's in memory, not in cookies)
  // So middleware just lets requests through; client-side ProtectedRoute handles redirect.
  // The real auth enforcement is at the API Gateway, not here.
  return NextResponse.next();
}

export const config = {
  matcher: ['/playground/:path*', '/submissions/:path*', '/me/:path*']
};
```

**Note:** Middleware here is a placeholder. Because our access token lives in memory (not a cookie), we can't validate it at the edge. Real protection happens:
1. Client-side via `<ProtectedRoute>` (UX redirect)
2. Server-side at the API Gateway (real security)

---

## 10. Constants (`lib/constants.ts`)

```typescript
// Maps Judge0 language IDs to Monaco language strings
export const LANGUAGE_ID_TO_MONACO: Record<number, string> = {
  50: 'c',
  54: 'cpp',
  62: 'java',
  63: 'javascript',
  71: 'python',
  73: 'rust',
  60: 'go'
};

// Default starter code per language
export const STARTER_CODE: Record<number, string> = {
  71: 'print("Hello, CodeArena!")\n',
  62: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodeArena!");\n    }\n}\n',
  54: '#include <iostream>\nusing namespace std;\n\nint main() {\n    cout << "Hello, CodeArena!" << endl;\n    return 0;\n}\n',
  63: 'console.log("Hello, CodeArena!");\n',
  50: '#include <stdio.h>\n\nint main() {\n    printf("Hello, CodeArena!\\n");\n    return 0;\n}\n',
  73: 'fn main() {\n    println!("Hello, CodeArena!");\n}\n',
  60: 'package main\n\nimport "fmt"\n\nfunc main() {\n    fmt.Println("Hello, CodeArena!")\n}\n'
};
```

---

## 11. `package.json` (Phase 6 starting point)

```json
{
  "name": "codearena-frontend",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "next": "15.0.3",
    "react": "19.0.0",
    "react-dom": "19.0.0",
    "@monaco-editor/react": "^4.6.0",
    "lucide-react": "^0.456.0"
  },
  "devDependencies": {
    "@types/node": "^22.9.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "typescript": "^5.6.3",
    "tailwindcss": "^4.0.0",
    "@tailwindcss/postcss": "^4.0.0",
    "postcss": "^8.4.49",
    "eslint": "^9.15.0",
    "eslint-config-next": "15.0.3"
  }
}
```

**Pin these versions exactly.** Next.js 15 and React 19 had real breaking changes in their 15.0.x line — random version bumps will cause confusing build errors. Use what's specified.

---

## 12. Dockerfile

```dockerfile
# Build stage
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
```

Requires `output: 'standalone'` in `next.config.js`.

---

## 13. `next.config.js`

```javascript
/** @type {import('next').NextConfig} */
module.exports = {
  output: 'standalone',
  reactStrictMode: true,
  env: {
    NEXT_PUBLIC_API_BASE: process.env.NEXT_PUBLIC_API_BASE
  }
};
```

In docker-compose, set `NEXT_PUBLIC_API_BASE=http://localhost:8080/api/v1` for local and `https://<your-domain>/api/v1` for prod.

**Note about NEXT_PUBLIC_*:** these are baked at build time, not runtime. For the GCP deploy, you'll rebuild the Docker image with the prod API base. We document this in the DevOps artifact.

---

## 14. Reference

This is artifact 4 of 5. See:
- **Artifact 1:** `judge0-architecture` — the "what and why"
- **Artifact 2:** `judge0-contracts` — gRPC, REST, schemas
- **Artifact 3:** `judge0-roadmap` — hour-by-hour plan
- **Artifact 5:** `judge0-devops` — Git, GitHub Actions, GCP, demo
