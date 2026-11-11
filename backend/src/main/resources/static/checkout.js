const form = document.querySelector("#checkout");
const message = document.querySelector("#message");
const payment = document.querySelector("#payment");
const link = document.querySelector("#payment-link");
const submit = form.querySelector("[type='submit']");
const refresh = document.querySelector("#refresh");
const amountInput = document.querySelector("#amount");
const orderInput = document.querySelector("#order-number");
const previewAmount = document.querySelector("#preview-amount");
const previewOrder = document.querySelector("#preview-order");
const keyInput = document.querySelector("#store-key");
const toggleKey = document.querySelector("#toggle-key");
let paymentId;

function formatAmount(value) {
    const amount = Number(value);
    if (!Number.isFinite(amount) || amount <= 0) return "— ₽";
    return new Intl.NumberFormat("ru-RU", {
        style: "currency",
        currency: "RUB",
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(amount);
}

amountInput.addEventListener("input", () => {
    previewAmount.textContent = formatAmount(amountInput.value);
});

orderInput.addEventListener("input", () => {
    previewOrder.textContent = orderInput.value.trim() || "—";
});

form.addEventListener("input", () => {
    form.querySelectorAll("[aria-invalid='true']").forEach((input) => input.removeAttribute("aria-invalid"));
});

toggleKey.addEventListener("click", () => {
    const visible = keyInput.type === "text";
    keyInput.type = visible ? "password" : "text";
    toggleKey.textContent = visible ? "Показать" : "Скрыть";
    toggleKey.setAttribute("aria-label", visible ? "Показать ключ" : "Скрыть ключ");
    toggleKey.setAttribute("aria-pressed", String(!visible));
});

async function request(url, options) {
    const response = await fetch(url, options);
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : {};
    if (!response.ok) {
        throw new Error(body.message || "Не удалось создать платёж. Проверьте подключение и параметры магазина.");
    }
    return body;
}

function showPayment(result) {
    paymentId = result.id;
    const labels = {
        CREATED: "Платёж создан. Перейдите на защищённую страницу CloudPayments, чтобы завершить оплату.",
        COMPLETED: "Оплата подтверждена.",
        CANCELLED: "Платёж отменён. Создайте новую ссылку, чтобы попробовать ещё раз."
    };
    message.textContent = labels[result.status] || `Статус платежа: ${result.status}`;
    message.dataset.state = result.status === "COMPLETED" ? "success" : "default";
    link.hidden = !result.paymentUrl || result.status !== "CREATED";
    refresh.hidden = !paymentId;
    if (!link.hidden) {
        link.href = result.paymentUrl;
    } else {
        link.removeAttribute("href");
    }
    payment.dataset.active = "true";
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    submit.disabled = true;
    submit.querySelector("span").textContent = "Создаём защищённую ссылку…";
    message.textContent = "Связываемся с CloudPayments. Это может занять несколько секунд.";
    message.dataset.state = "default";
    link.hidden = true;
    refresh.hidden = true;

    const data = new FormData(form);
    try {
        showPayment(await request("/api/payments", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Authorization: "Bearer " + data.get("key")
            },
            body: JSON.stringify({
                orderNumber: data.get("orderNumber"),
                amount: data.get("amount")
            })
        }));
    } catch (error) {
        message.textContent = error.message;
        message.dataset.state = "error";
        payment.dataset.active = "false";
    } finally {
        submit.disabled = false;
        submit.querySelector("span").textContent = "Создать платёж";
    }
});

refresh.addEventListener("click", async () => {
    if (!paymentId) return;
    refresh.disabled = true;
    refresh.textContent = "Проверяем…";
    try {
        showPayment(await request("/api/payments/" + encodeURIComponent(paymentId)));
    } catch (error) {
        message.textContent = error.message;
        message.dataset.state = "error";
    } finally {
        refresh.disabled = false;
        refresh.textContent = "Проверить статус платежа";
    }
});
