const form = document.querySelector("#checkout");
const message = document.querySelector("#message");
const submit = form.querySelector("[type='submit']");
const refresh = document.querySelector("#refresh");
const amountInput = document.querySelector("#amount");
const orderInput = document.querySelector("#order-number");
const previewAmount = document.querySelector("#preview-amount");
const previewOrder = document.querySelector("#preview-order");
const merchantName = document.querySelector("#merchant-name");
const keyInput = document.querySelector("#store-key");
const toggleKey = document.querySelector("#toggle-key");
const methods = document.querySelector("#payment-methods");
const container = document.querySelector("#cloudpayments-payment-blocks");
const params = new URLSearchParams(location.search);
const paymentId = params.get("paymentId");
let blocks;
let sdk;
let awaitingConfirmation = false;

function formatAmount(value) {
    const amount = Number(value);
    if (!Number.isFinite(amount) || amount <= 0) return "— ₽";
    return new Intl.NumberFormat("ru-RU", {style: "currency", currency: "RUB"}).format(amount);
}

function showMessage(text, state = "default") {
    message.textContent = text;
    message.dataset.state = state;
}

async function request(url, options) {
    const response = await fetch(url, options);
    const contentType = response.headers.get("content-type") || "";
    if (!/\b(?:application\/json|application\/[\w.+-]+\+json)\b/i.test(contentType)) {
        throw new Error(`Платёжный API вернул неожиданный ответ (HTTP ${response.status}). Проверьте адрес backend.`);
    }
    let body;
    try {
        body = await response.json();
    } catch {
        throw new Error("Платёжный API вернул повреждённый ответ.");
    }
    if (!response.ok) {
        throw new Error(body?.detail || body?.message || `Запрос не выполнен (HTTP ${response.status}).`);
    }
    if (!body?.id || !body?.status) {
        throw new Error("Платёжный API не вернул номер или статус платежа.");
    }
    return body;
}

function loadPaymentBlocks() {
    if (window.cp?.PaymentBlocks) return Promise.resolve();
    if (!sdk) {
        const script = document.createElement("script");
        script.src = "https://widget.cloudpayments.ru/bundles/paymentblocks.js";
        sdk = new Promise((resolve, reject) => {
            script.onload = () => window.cp?.PaymentBlocks
                ? resolve()
                : reject(new Error("Форма CloudPayments недоступна."));
            script.onerror = () => reject(new Error("Не удалось загрузить форму CloudPayments."));
            document.head.append(script);
        }).catch((error) => {
            script.remove();
            sdk = undefined;
            throw error;
        });
    }
    return sdk;
}

function hideMethods() {
    if (blocks) {
        blocks.off("success");
        blocks.off("fail");
        blocks.unmount();
        blocks = undefined;
    }
    methods.hidden = true;
}

async function mountMethods(payment) {
    if (blocks) return;
    showMessage("Загружаем способы оплаты…");
    await loadPaymentBlocks();
    methods.hidden = false;
    try {
        blocks = new window.cp.PaymentBlocks({
            publicTerminalId: payment.publicTerminalId,
            externalId: payment.id,
            paymentSchema: "Single",
            amount: payment.amount,
            currency: payment.currency,
            description: payment.description,
            language: "ru-RU"
        }, {
            appearance: {colors: {primaryButtonColor: "#215646", primaryButtonTextColor: "#ffffff"}}
        });
        await blocks.mount(container);
        blocks.on("success", () => {
            awaitingConfirmation = true;
            hideMethods();
            waitForConfirmation();
        });
        blocks.on("fail", () => {
            showMessage("Оплата не прошла. Можно повторить попытку.", "error");
        });
        showMessage("");
    } catch (error) {
        hideMethods();
        throw error;
    }
}

async function loadPayment() {
    const payment = await request("/api/payments/" + encodeURIComponent(paymentId) + "/embedded");
    previewOrder.textContent = payment.orderNumber;
    previewAmount.textContent = formatAmount(payment.amount);
    merchantName.textContent = payment.merchantName;
    if (payment.status === "COMPLETED") {
        hideMethods();
        showMessage("Оплата подтверждена.", "success");
        refresh.hidden = true;
    } else if (payment.status === "CANCELLED") {
        hideMethods();
        showMessage("Платёж отменён.", "error");
        refresh.hidden = true;
    } else if (payment.canPay && !awaitingConfirmation) {
        await mountMethods(payment);
    } else {
        hideMethods();
        showMessage(payment.processing || awaitingConfirmation
            ? "Ожидаем подтверждения оплаты."
            : "Сейчас оплатить этот заказ нельзя.");
    }
    return payment.status;
}

async function waitForConfirmation() {
    refresh.disabled = true;
    showMessage("Проверяем оплату…");
    try {
        for (let attempt = 0; attempt < 15; attempt++) {
            if (await loadPayment() !== "CREATED") return;
            await new Promise((resolve) => setTimeout(resolve, 2000));
        }
        showMessage("Подтверждение ещё не получено. Проверьте статус позже.");
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        refresh.disabled = false;
    }
}

async function refreshPayment() {
    refresh.disabled = true;
    try {
        await loadPayment();
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        refresh.disabled = false;
    }
}

if (params.has("paymentId")) {
    document.querySelector("#merchant-tools").hidden = true;
    document.querySelector("#merchant-back").hidden = true;
    document.querySelector("#checkout-art").hidden = true;
    document.querySelector("#page-title").textContent = "Оплата заказа";
    document.querySelector("#intro-copy").textContent = "Выберите удобный способ оплаты.";
    previewOrder.textContent = "—";
    previewAmount.textContent = "— ₽";
    merchantName.textContent = "Загружаем магазин…";
    refresh.hidden = false;
    refresh.addEventListener("click", refreshPayment);
    showMessage("Загружаем заказ…");
    refreshPayment();
} else {
    amountInput.addEventListener("input", () => {
        previewAmount.textContent = formatAmount(amountInput.value);
    });
    orderInput.addEventListener("input", () => {
        previewOrder.textContent = orderInput.value.trim() || "—";
    });
    toggleKey.addEventListener("click", () => {
        const visible = keyInput.type === "text";
        keyInput.type = visible ? "password" : "text";
        toggleKey.textContent = visible ? "Показать" : "Скрыть";
        toggleKey.setAttribute("aria-label", visible ? "Показать ключ" : "Скрыть ключ");
        toggleKey.setAttribute("aria-pressed", String(!visible));
    });
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        submit.disabled = true;
        showMessage("Создаём тестовый платёж…");
        const data = new FormData(form);
        try {
            const payment = await request("/api/payments/embedded", {
                method: "POST",
                headers: {"Content-Type": "application/json", Authorization: "Bearer " + data.get("key")},
                body: JSON.stringify({orderNumber: data.get("orderNumber"), amount: data.get("amount")})
            });
            location.assign("/?paymentId=" + encodeURIComponent(payment.id));
        } catch (error) {
            showMessage(error.message, "error");
        } finally {
            submit.disabled = false;
        }
    });
}
