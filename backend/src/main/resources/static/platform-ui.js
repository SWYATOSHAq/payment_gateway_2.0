export const PAYMENT_PATH = "/api/cabinet/payments";
export const MERCHANT_PATH = "/api/cabinet/merchants";
export const roles = {VIEWER: "Наблюдатель", EDITOR: "Редактор", ADMIN: "Администратор"};
export const statuses = {CREATED: "Ожидает оплаты", COMPLETED: "Оплачен", CANCELLED: "Отменён"};
export const categories = {SMALL: "До 10 000 ₽", MEDIUM: "От 10 000 до 100 000 ₽", LARGE: "От 100 000 ₽"};

export const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
}[character]));

export const money = new Intl.NumberFormat("ru-RU", {style: "currency", currency: "RUB"}).format;
const formatter = new Intl.DateTimeFormat("ru-RU", {dateStyle: "short", timeStyle: "short"});
export const date = (value) => value ? formatter.format(new Date(value)) : "—";
export const status = (value) => `<span class="status ${({
    CREATED: "s-pending", COMPLETED: "s-success", CANCELLED: "s-cancelled"
}[value] || "")}">${esc(statuses[value] || value)}</span>`;
export const canEdit = (user) => user.role === "EDITOR" || user.role === "ADMIN";

export function heading(title, actions = "") {
    return `<header class="page-head"><h1>${esc(title)}</h1>${actions ? `<div class="actions">${actions}</div>` : ""}</header>`;
}

export function field(label, input) {
    return `<label class="field"><span>${esc(label)}</span>${input}</label>`;
}

export function options(labels, selected, placeholder = "") {
    return (placeholder ? `<option value="">${esc(placeholder)}</option>` : "") +
        Object.entries(labels).map(([value, label]) => `<option value="${esc(value)}"${value === String(selected) ? " selected" : ""}>${esc(label)}</option>`).join("");
}

export function table(headings, rows, empty = "Записей пока нет.") {
    return rows.length ? `<div class="table-scroll"><table><thead><tr>${headings.map((title) =>
        `<th scope="col">${esc(title)}</th>`).join("")}</tr></thead><tbody>${rows.join("")}</tbody></table></div>`
        : `<p class="empty">${esc(empty)}</p>`;
}

export function href(route, params = new URLSearchParams()) {
    return "#" + route + (params.size ? "?" + params.toString() : "");
}

export function pagination(route, params, data) {
    const {page, size, total} = data;
    const pages = Math.ceil(total / size);
    const link = (target, label) => {
        const query = new URLSearchParams(params);
        query.set("page", target);
        return `<a class="btn" href="${esc(href(route, query))}">${label}</a>`;
    };
    return `<nav class="cabinet-pagination" aria-label="Страницы">
        ${page > 0 ? link(page - 1, "Назад") : ""}
        <span>${total ? `Страница ${page + 1} из ${pages} · Всего ${total}` : "Всего 0"}</span>
        ${page + 1 < pages ? link(page + 1, "Далее") : ""}
    </nav>`;
}

export function filters(form, route) {
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const query = new URLSearchParams();
        for (const [key, value] of new FormData(form)) {
            if (String(value).trim()) query.set(key, value);
        }
        location.hash = href(route, query);
    });
}

export function notice(scope, text, error = false) {
    const element = scope.querySelector("[data-notice]");
    if (!element) return;
    element.hidden = !text;
    element.textContent = text || "";
    element.dataset.error = String(error);
}

export async function action(context, button, work) {
    if (button?.disabled) return;
    if (button) button.disabled = true;
    notice(context.scope, "");
    try {
        await work();
    } catch (error) {
        if (!context.handleSession()) notice(context.scope, error.message || "Запрос не выполнен.", true);
    } finally {
        if (button) button.disabled = false;
    }
}

export function bindForm(context, form, work) {
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        action(context, event.submitter, () => work(new FormData(form), form));
    });
}
