const api = async (url, options = {}) => {
    const response = await fetch(url, {
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        ...options
    });
    if (response.status === 204) return null;
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `Request failed (${response.status})`);
    return data;
};

const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[character]));

const formatDate = value => new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium', timeStyle: 'short'
}).format(new Date(value));

const setMessage = (message, text, isError = true) => {
    message.textContent = text;
    message.classList.toggle('is-error', isError);
    message.classList.toggle('is-success', !isError);
};

async function updateNavigation() {
    const login = document.querySelector('#login-link');
    if (!login) return;
    try {
        const user = await api('/api/auth/me');
        login.hidden = true;
        document.querySelector('#register-link').hidden = true;
        document.querySelector('#my-bookings-link').hidden = false;
        document.querySelector('#logout-button').hidden = false;
        document.querySelector('#logout-button').addEventListener('click', async () => {
            await api('/api/auth/logout', { method: 'POST' });
            window.location.href = '/';
        });
        if (user.role === 'ADMIN') document.querySelector('#admin-link').hidden = false;

        // Show admin session banner on admin page
        const banner = document.querySelector('#admin-session-banner');
        if (banner && user.role === 'ADMIN') {
            document.querySelector('#admin-banner-email').textContent = user.email || 'admin';
            banner.hidden = false;
            document.querySelector('#admin-exit-btn').addEventListener('click', async () => {
                await api('/api/auth/logout', { method: 'POST' });
                window.location.href = '/';
            });
        }
    } catch (_) {
        // Guests see the default login and register links.
    }
}

const formatDay = value => new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })
    .format(new Date(`${value}T00:00:00`));

async function loadHome() {
    const nowGrid = document.querySelector('#movie-grid');
    const soonGrid = document.querySelector('#coming-soon-grid');
    const soonSection = document.querySelector('#coming-soon-section');
    const search = document.querySelector('#movie-search');
    const langSelect = document.querySelector('#filter-language');
    const genreSelect = document.querySelector('#filter-genre');
    const d = new Date();
    const today = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

    const card = (movie, soon) => `
        <a class="movie-card" href="/movies/${movie.id}">
            <img src="${escapeHtml(movie.posterUrl || '')}" alt="Poster for ${escapeHtml(movie.title)}"
                 onerror="this.classList.add('image-missing');this.removeAttribute('src')">
            <div class="movie-card-body"><p class="eyebrow">${escapeHtml(movie.genre)} · ${movie.durationMinutes} min${movie.certificate ? ' · ' + escapeHtml(movie.certificate) : ''}</p>
                <h3>${escapeHtml(movie.title)}</h3>
                <p class="muted">${escapeHtml(movie.language)}${soon ? ' · Releases ' + escapeHtml(formatDay(movie.releaseDate)) : ''}</p>
                <span class="text-link">${soon ? 'View details →' : 'View showtimes →'}</span></div>
        </a>`;

    try {
        const movies = await api('/api/movies');
        const fill = (select, values) => values.forEach(v => select.insertAdjacentHTML('beforeend',
            `<option value="${escapeHtml(v)}">${escapeHtml(v)}</option>`));
        fill(langSelect, [...new Set(movies.map(m => m.language))].sort());
        fill(genreSelect, [...new Set(movies.map(m => m.genre))].sort());

        const render = () => {
            const q = search.value.trim().toLowerCase();
            const shown = movies.filter(m =>
                (!q || m.title.toLowerCase().includes(q)) &&
                (!langSelect.value || m.language === langSelect.value) &&
                (!genreSelect.value || m.genre === genreSelect.value));
            const soon = shown.filter(m => m.releaseDate && m.releaseDate > today);
            const now = shown.filter(m => !(m.releaseDate && m.releaseDate > today));
            nowGrid.innerHTML = now.length ? now.map(m => card(m, false)).join('')
                : '<p class="muted">No movies match your filters.</p>';
            soonGrid.innerHTML = soon.map(m => card(m, true)).join('');
            soonSection.hidden = soon.length === 0;
        };
        [search, langSelect, genreSelect].forEach(el => el.addEventListener('input', render));
        render();
    } catch (error) {
        nowGrid.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)}</p>`;
    }
}

async function loadMovie() {
    const id = document.body.dataset.movieId;
    const detail = document.querySelector('#movie-detail');
    const showList = document.querySelector('#show-list');
    try {
        const [movie, shows] = await Promise.all([api(`/api/movies/${id}`), api(`/api/movies/${id}/shows`)]);
        detail.innerHTML = `<img class="detail-poster" src="${escapeHtml(movie.posterUrl || '')}"
                alt="Poster for ${escapeHtml(movie.title)}" onerror="this.classList.add('image-missing');this.removeAttribute('src')">
            <div><p class="eyebrow">${escapeHtml(movie.genre)} · ${movie.durationMinutes} min · ${escapeHtml(movie.language)}${movie.certificate ? ' · ' + escapeHtml(movie.certificate) : ''}</p>
                <h1>${escapeHtml(movie.title)}</h1><p class="detail-description">${escapeHtml(movie.description)}</p></div>`;
        showList.innerHTML = shows.length ? shows.map(show => `<article class="show-card">
            <div><strong>${escapeHtml(formatDate(show.showTime))}</strong>
                <p class="muted">${escapeHtml(show.theatreName)} · ${escapeHtml(show.city)} · ${escapeHtml(show.screenName)}</p></div>
            <div class="show-action"><span class="price">₹${Number(show.price).toFixed(2)}</span>
                <a class="button button-small" href="/shows/${show.id}/seats">Select seats</a></div></article>`).join('')
            : '<p class="muted">There are no showtimes for this movie yet.</p>';
    } catch (error) {
        detail.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)}</p>`;
        showList.replaceChildren();
    }
}

async function loadSeats() {
    const showId = document.body.dataset.showId;
    const grid = document.querySelector('#seat-grid');
    const selected = new Set();
    const summary = document.querySelector('#selection-summary');
    const bookButton = document.querySelector('#book-seats');
    try {
        const seats = await api(`/api/shows/${showId}/seats`);
        grid.innerHTML = seats.map(seat => {
            const booked = seat.status === 'BOOKED';
            return `<button class="seat ${booked ? 'booked' : 'available'}" data-seat-id="${seat.id}"
                aria-label="Row ${escapeHtml(seat.rowLabel)}, seat ${seat.seatNumber}${booked ? ', booked' : ''}"
                ${booked ? 'disabled' : ''}>${escapeHtml(seat.rowLabel)}${seat.seatNumber}</button>`;
        }).join('');
        grid.addEventListener('click', event => {
            const button = event.target.closest('.seat:not(:disabled)');
            if (!button) return;
            const seatId = Number(button.dataset.seatId);
            if (selected.has(seatId)) {
                selected.delete(seatId);
                button.classList.replace('selected', 'available');
            } else {
                selected.add(seatId);
                button.classList.replace('available', 'selected');
            }
            summary.textContent = `${selected.size} seat${selected.size === 1 ? '' : 's'}`;
            bookButton.disabled = selected.size === 0;
        });
        bookButton.addEventListener('click', async () => {
            bookButton.disabled = true;
            try {
                const booking = await api('/api/bookings', {
                    method: 'POST', body: JSON.stringify({ showId: Number(showId), seatIds: [...selected] })
                });
                sessionStorage.setItem('latestBookingId', String(booking.id));
                if (!booking.paymentLinkUrl) throw new Error('Payment link could not be generated. Please try again.');
                window.location.href = booking.paymentLinkUrl;
            } catch (error) {
                const hint = document.querySelector('#seat-hint');
                if (error.message && error.message.toLowerCase().includes('log in')) {
                    hint.innerHTML = `<span class="is-error">${escapeHtml(error.message)} <a href="/login" class="text-link">Log in here</a></span>`;
                } else {
                    hint.textContent = error.message;
                    hint.classList.add('is-error');
                }
                bookButton.disabled = selected.size === 0;
            }
        });
    } catch (error) {
        grid.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)}</p>`;
    }
}

async function loadConfirmation() {
    const bookingId = document.body.dataset.bookingId;
    const details = document.querySelector('#confirmation-details');
    try {
        const booking = await api(`/api/bookings/${bookingId}`);
        details.innerHTML = `<strong>${escapeHtml(booking.movieTitle)}</strong><br>
            ${escapeHtml(formatDate(booking.showTime))}<br>${escapeHtml(booking.theatreName)} · ${escapeHtml(booking.screenName)}<br>
            Seats: ${escapeHtml(booking.seats.join(', '))}<br>Total: ₹${Number(booking.totalAmount).toFixed(2)}<br>
            Booking #${booking.id}`;
    } catch (error) {
        details.textContent = error.message;
    }
}

async function loadBookings() {
    const list = document.querySelector('#booking-list');
    if (!list.dataset.bound) {
        list.dataset.bound = 'true';
        list.addEventListener('click', async event => {
            const button = event.target.closest('.cancel-booking');
            if (!button || !window.confirm('Cancel this reservation?')) return;
            button.disabled = true;
            try { await api(`/api/bookings/${button.dataset.bookingId}`, { method: 'DELETE' }); await loadBookings(); }
            catch (error) { button.disabled = false; window.alert(error.message); }
        });
    }
    try {
        const user = await api('/api/auth/me');
        const bookings = await api(`/api/users/${user.id}/bookings`);
        list.innerHTML = bookings.length ? bookings.map(booking => `<article class="booking-card">
            <div class="booking-card-top"><div><p class="eyebrow">BOOKING #${booking.id}</p><h2>${escapeHtml(booking.movieTitle)}</h2></div>
                <span class="status ${booking.status.toLowerCase()}">${escapeHtml(booking.status)}</span></div>
            <p class="muted">${escapeHtml(formatDate(booking.showTime))} · ${escapeHtml(booking.theatreName)} · ${escapeHtml(booking.screenName)}</p>
            <p>Seats: ${escapeHtml(booking.seats.join(', ') || '—')} · Total: ₹${Number(booking.totalAmount).toFixed(2)}</p>
            ${booking.status === 'CONFIRMED' ? `<button class="button button-secondary cancel-booking" data-booking-id="${booking.id}">Cancel booking</button>` : ''}
        </article>`).join('') : '<p class="muted">You have no bookings yet. <a href="/">Browse movies</a></p>';
    } catch (error) {
        list.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)} <a href="/login">Log in</a></p>`;
    }
}

function wireAccountForms() {
    const loginForm = document.querySelector('#login-form');
    const registerForm = document.querySelector('#register-form');
    const form = loginForm || registerForm;
    if (!form) return;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const message = document.querySelector('#form-message');
        const values = Object.fromEntries(new FormData(form));
        try {
            await api(loginForm ? '/api/auth/login' : '/api/auth/register', {
                method: 'POST', body: JSON.stringify(values)
            });
            window.location.href = '/';
        } catch (error) { setMessage(message, error.message); }
    });
}

async function wireMovieForm() {
    const form = document.querySelector('#movie-form');
    if (!form) return;
    try {
        const user = await api('/api/auth/me');
        if (user.role !== 'ADMIN') throw new Error('Administrator access is required.');
    } catch (error) {
        form.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)} <a href="/admin/login">Log in as an administrator</a></p>`;
        return;
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const message = document.querySelector('#form-message');
        const values = Object.fromEntries(new FormData(form));
        values.durationMinutes = Number(values.durationMinutes);
        values.posterUrl = values.posterUrl || null;
        values.releaseDate = values.releaseDate || null;
        values.certificate = values.certificate || null;
        try {
            const movie = await api('/api/movies', { method: 'POST', body: JSON.stringify(values) });
            setMessage(message, `Added “${movie.title}”.`, false);
            form.reset();
            const fileHint = document.querySelector('#poster-file-hint');
            if (fileHint) fileHint.textContent = '';
            if (window.refreshShowOptions) window.refreshShowOptions();
            if (window.refreshDeleteOptions) window.refreshDeleteOptions();
        } catch (error) { setMessage(message, error.message); }
    });

    // Poster file picker: convert image to data URL and place in URL field
    const fileInput = form.querySelector('#poster-file-input');
    const urlInput = form.querySelector('#poster-url-input');
    const fileHint = document.querySelector('#poster-file-hint');
    if (fileInput && urlInput) {
        fileInput.addEventListener('change', () => {
            const file = fileInput.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = e => {
                urlInput.value = e.target.result;
                if (fileHint) fileHint.textContent = `Loaded: ${file.name} (${(file.size / 1024).toFixed(1)} KB)`;
            };
            reader.readAsDataURL(file);
        });
    }
}

async function wireShowForm() {
    const form = document.querySelector('#show-form');
    if (!form) return;
    const movieSelect = form.querySelector('[name=movieId]');
    const screenSelect = form.querySelector('[name=screenId]');
    const message = document.querySelector('#show-message');
    const fill = (select, items, label) => {
        select.innerHTML = items.map(i => `<option value="${i.id}">${escapeHtml(label(i))}</option>`).join('');
    };
    const refresh = async () => {
        const [movies, screens] = await Promise.all([api('/api/movies'), api('/api/admin/screens')]);
        fill(movieSelect, movies, m => `${m.title} (${m.language})`);
        fill(screenSelect, screens, s => s.label);
    };
    window.refreshShowOptions = () => refresh().catch(() => {});
    try { await refresh(); } catch (error) {
        form.innerHTML = `<p class="form-message is-error">${escapeHtml(error.message)} <a href="/admin/login">Log in as an administrator</a></p>`;
        return;
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const values = Object.fromEntries(new FormData(form));
        values.movieId = Number(values.movieId);
        values.screenId = Number(values.screenId);
        values.price = Number(values.price);
        try {
            const show = await api('/api/admin/shows', { method: 'POST', body: JSON.stringify(values) });
            setMessage(message, `Added a show on ${formatDate(show.showTime)}.`, false);
        } catch (error) { setMessage(message, error.message); }
    });
}

function wireAdminLoginForm() {
    const form = document.querySelector('#admin-login-form');
    if (!form) return;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const message = document.querySelector('#form-message');
        const values = Object.fromEntries(new FormData(form));
        try {
            await api('/api/auth/admin-login', { method: 'POST', body: JSON.stringify(values) });
            window.location.href = '/admin';
        } catch (error) { setMessage(message, error.message); }
    });
}

async function wireDeleteMovieForm() {
    const form = document.querySelector('#delete-movie-form');
    if (!form) return;
    const select = form.querySelector('#delete-movie-select');
    const message = document.querySelector('#delete-message');
    const refresh = async () => {
        const movies = await api('/api/movies');
        if (!movies.length) {
            select.innerHTML = '<option value="">— no movies —</option>';
            return;
        }
        select.innerHTML = movies.map(m =>
            `<option value="${m.id}">${escapeHtml(m.title)} (${escapeHtml(m.language)})</option>`
        ).join('');
    };
    window.refreshDeleteOptions = () => refresh().catch(() => {});
    try { await refresh(); } catch (error) {
        select.innerHTML = '<option value="">— error loading —</option>';
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const id = select.value;
        if (!id) return;
        const label = select.options[select.selectedIndex].text;
        if (!window.confirm(`Delete "${label}"? This cannot be undone.`)) return;
        try {
            await api(`/api/movies/${id}`, { method: 'DELETE' });
            setMessage(message, `"${label}" was deleted.`, false);
            await refresh();
            if (window.refreshShowOptions) window.refreshShowOptions();
        } catch (error) { setMessage(message, error.message); }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    updateNavigation();
    const page = document.body.dataset.page;
    if (page === 'home') loadHome();
    if (page === 'movie') loadMovie();
    if (page === 'seats') loadSeats();
    if (page === 'confirmation') loadConfirmation();
    if (page === 'bookings') loadBookings();
    if (page === 'login' || page === 'register') wireAccountForms();
    if (page === 'admin-login') wireAdminLoginForm();
    if (page === 'admin') wireMovieForm();
    if (page === 'admin') wireShowForm();
    if (page === 'admin') wireDeleteMovieForm();
});
