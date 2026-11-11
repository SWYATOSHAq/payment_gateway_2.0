const SESSION_KEY = "titan-pay-refresh";
let session = null;
let refreshPromise;

export class ApiError extends Error {
    constructor(status, body) {
        super(body?.detail || body?.message || ({
            400: "Проверьте поля запроса.",
            401: "Проверьте логин и пароль или войдите заново.",
            403: "Для этого действия недостаточно прав.",
            404: "Запись не найдена.",
            409: "Данные изменились. Обновите страницу.",
            422: "Проверьте данные перед применением."
        }[status] || `Запрос не выполнен (HTTP ${status}).`));
        this.status = status;
        this.body = body;
    }
}

export function currentUser() {
    return session?.user;
}

function setSession(data) {
    session = data;
    sessionStorage.setItem(SESSION_KEY, data.refreshToken);
    return data.user;
}

function clearSession() {
    session = null;
    sessionStorage.removeItem(SESSION_KEY);
}

async function send(path, {method = "GET", body, token, accept = "application/json"} = {}) {
    const headers = {Accept: accept};
    if (token) headers.Authorization = "Bearer " + token;
    if (body !== undefined && !(body instanceof FormData)) {
        headers["Content-Type"] = "application/json";
        body = JSON.stringify(body);
    }
    return fetch(path, {method, headers, body, cache: "no-store"});
}

async function read(response) {
    if (response.status === 204) return null;
    const type = response.headers.get("Content-Type") || "";
    const data = /\b(?:application\/json|application\/[\w.+-]+\+json)\b/i.test(type)
        ? await response.json().catch(() => null)
        : null;
    if (!response.ok || data === null) throw new ApiError(response.status, data);
    return data;
}

function refresh() {
    const token = sessionStorage.getItem(SESSION_KEY);
    if (!token) return Promise.resolve(null);
    if (!refreshPromise) {
        refreshPromise = send("/api/auth/refresh", {method: "POST", body: {refreshToken: token}})
            .then(read)
            .then((data) => sessionStorage.getItem(SESSION_KEY) === token ? setSession(data) : null)
            .catch((error) => {
                if (error.status === 401 && sessionStorage.getItem(SESSION_KEY) === token) clearSession();
                throw error;
            })
            .finally(() => { refreshPromise = undefined; });
    }
    return refreshPromise;
}

export function restoreSession() {
    return refresh();
}

export async function login(body) {
    return setSession(await read(await send("/api/auth/login", {method: "POST", body})));
}

export async function changePassword(body) {
    return setSession(await read(await send("/api/auth/change-password", {
        method: "POST", body, token: session?.accessToken
    })));
}

export async function logout() {
    try {
        await api("/api/auth/logout", {method: "POST"});
    } finally {
        clearSession();
    }
}

export async function api(path, options = {}) {
    let token = session?.accessToken;
    let response = await send(path, {...options, token});
    if (response.status === 401 && (session?.accessToken !== token || await refresh()) && session) {
        token = session.accessToken;
        response = await send(path, {...options, token});
    }
    if (response.status === 401 && session?.accessToken === token) clearSession();
    if (options.download && response.ok) return response.blob();
    return read(response);
}
