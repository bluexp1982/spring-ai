import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { isTokenValid } from '@/utils/auth';

interface AuthState {
  token: string | null;
  username: string | null;
  displayName: string | null;
}

interface AuthContextType extends AuthState {
  isAuthenticated: boolean;
  login: (token: string, username: string, displayName: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

const TOKEN_KEY = 'admin_token';
const USERNAME_KEY = 'admin_username';
const DISPLAY_NAME_KEY = 'admin_display_name';

function loadAuthState(): AuthState {
  const token = localStorage.getItem(TOKEN_KEY);
  // token 不存在、已过期或非法时视为未登录，并清掉残留的登录信息
  if (!isTokenValid(token)) {
    clearAuthState();
    return { token: null, username: null, displayName: null };
  }
  return {
    token,
    username: localStorage.getItem(USERNAME_KEY),
    displayName: localStorage.getItem(DISPLAY_NAME_KEY),
  };
}

function saveAuthState(token: string, username: string, displayName: string) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);
  localStorage.setItem(DISPLAY_NAME_KEY, displayName);
}

function clearAuthState() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
  localStorage.removeItem(DISPLAY_NAME_KEY);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(loadAuthState);
  const navigate = useNavigate();

  const login = useCallback((token: string, username: string, displayName: string) => {
    saveAuthState(token, username, displayName);
    setState({ token, username, displayName });
  }, []);

  const logout = useCallback(() => {
    clearAuthState();
    setState({ token: null, username: null, displayName: null });
    navigate('/admin/login', { replace: true });
  }, [navigate]);

  return (
    <AuthContext.Provider
      value={{
        ...state,
        isAuthenticated: !!state.token,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}