/**
 * PROPIO - SaaS Theme, Interactions, Counters & Visuals
 */
(function () {
  'use strict';

  const STORAGE_KEY = 'propio-theme-preference';

  // ---------------------------------------------------------------------------
  // 1. Theme Management (Light / Dark Mode with System Preference Fallback)
  // ---------------------------------------------------------------------------
  function getPreferredTheme() {
    const storedTheme = localStorage.getItem(STORAGE_KEY);
    if (storedTheme) {
      return storedTheme;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(STORAGE_KEY, theme);

    // Update icon state
    const themeIcon = document.getElementById('theme-toggle-icon');
    if (themeIcon) {
      if (theme === 'dark') {
        themeIcon.className = 'bi bi-sun-fill text-warning';
      } else {
        themeIcon.className = 'bi bi-moon-stars-fill text-muted';
      }
    }

    // Refresh chart theme styling if charts are loaded
    if (window.propioCharts) {
      window.propioCharts.updateTheme(theme);
    }
  }

  // Initial theme application
  const initialTheme = getPreferredTheme();
  applyTheme(initialTheme);

  // Listen for OS system theme changes if user hasn't set an explicit override
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
    if (!localStorage.getItem(STORAGE_KEY)) {
      applyTheme(e.matches ? 'dark' : 'light');
    }
  });

  // ---------------------------------------------------------------------------
  // 2. Animated Number Counters
  // ---------------------------------------------------------------------------
  function animateCounters() {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const counters = document.querySelectorAll('[data-counter]');

    counters.forEach((el) => {
      const targetStr = el.getAttribute('data-counter') || '0';
      const prefix = el.getAttribute('data-prefix') || '';
      const suffix = el.getAttribute('data-suffix') || '';
      const decimals = parseInt(el.getAttribute('data-decimals') || '0', 10);
      const targetVal = parseFloat(targetStr.replace(/[^0-9.-]+/g, ''));

      if (isNaN(targetVal)) return;

      if (prefersReducedMotion) {
        el.textContent = `${prefix}${targetVal.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}${suffix}`;
        return;
      }

      const duration = 1200; // ms
      const startTime = performance.now();

      function updateCounter(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);

        // Ease-out cubic calculation
        const easeOutProgress = 1 - Math.pow(1 - progress, 3);
        const currentVal = targetVal * easeOutProgress;

        const formatted = currentVal.toLocaleString('en-US', {
          minimumFractionDigits: decimals,
          maximumFractionDigits: decimals
        });

        el.textContent = `${prefix}${formatted}${suffix}`;

        if (progress < 1) {
          requestAnimationFrame(updateCounter);
        } else {
          el.textContent = `${prefix}${targetVal.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}${suffix}`;
        }
      }

      requestAnimationFrame(updateCounter);
    });
  }

  // ---------------------------------------------------------------------------
  // 3. Mobile Sidebar Toggle & Interactivity
  // ---------------------------------------------------------------------------
  function setupSidebar() {
    const toggleBtn = document.getElementById('sidebar-toggle-btn');
    const sidebar = document.getElementById('app-sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');

    if (!sidebar || !toggleBtn || !backdrop) return;

    function openSidebar() {
      sidebar.classList.add('show');
      backdrop.classList.add('show');
    }

    function closeSidebar() {
      sidebar.classList.remove('show');
      backdrop.classList.remove('show');
    }

    toggleBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (sidebar.classList.contains('show')) {
        closeSidebar();
      } else {
        openSidebar();
      }
    });

    backdrop.addEventListener('click', closeSidebar);

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && sidebar.classList.contains('show')) {
        closeSidebar();
      }
    });
  }

  // ---------------------------------------------------------------------------
  // 4. Chart.js Dashboard Visualizations
  // ---------------------------------------------------------------------------
  function setupCharts() {
    if (typeof Chart === 'undefined') return;

    // Global Chart.js defaults
    Chart.defaults.font.family = "'Inter', sans-serif";
    Chart.defaults.font.size = 12;

    const isDark = () => document.documentElement.getAttribute('data-bs-theme') === 'dark';

    // 1. Property Units Donut Chart
    const unitsCanvas = document.getElementById('unitsDonutChart');
    let unitsChart = null;

    if (unitsCanvas) {
      const rawOccupied = parseInt(unitsCanvas.getAttribute('data-occupied') || '0', 10);
      const rawBooked = parseInt(unitsCanvas.getAttribute('data-booked') || '0', 10);
      const rawVacant = parseInt(unitsCanvas.getAttribute('data-vacant') || '0', 10);
      const rawMaintenance = parseInt(unitsCanvas.getAttribute('data-maintenance') || '0', 10);

      const donutLabels = ['Occupied', 'Booked', 'Vacant'];
      const donutValues = [rawOccupied, rawBooked, rawVacant];
      const donutColors = [
        '#5B43EA', // Occupied (Purple)
        '#FF4B93', // Booked (Pink)
        isDark() ? '#2D3748' : '#CFEBF1' // Vacant (Soft Light/Navy)
      ];

      if (rawMaintenance > 0) {
        donutLabels.push('Maintenance');
        donutValues.push(rawMaintenance);
        donutColors.push('#F59E0B');
      }

      const ctxUnits = unitsCanvas.getContext('2d');
      unitsChart = new Chart(ctxUnits, {
        type: 'doughnut',
        data: {
          labels: donutLabels,
          datasets: [{
            data: donutValues,
            backgroundColor: donutColors,
            borderWidth: 0,
            hoverOffset: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '76%',
          plugins: {
            legend: { display: false },
            tooltip: {
              backgroundColor: isDark() ? '#1E293B' : '#0F172A',
              titleColor: '#FFFFFF',
              bodyColor: '#E2E8F0',
              padding: 10,
              cornerRadius: 10,
              boxPadding: 6
            }
          },
          animation: {
            animateScale: true,
            animateRotate: true,
            duration: 1200
          }
        }
      });
    }

    // 2. Rental Payments Spline Wave Area Chart
    const paymentsCanvas = document.getElementById('rentalPaymentsChart');
    let paymentsChart = null;

    if (paymentsCanvas) {
      const ctxPayments = paymentsCanvas.getContext('2d');

      function getGradient(ctx, height) {
        const gradient = ctx.createLinearGradient(0, 0, 0, height);
        gradient.addColorStop(0, 'rgba(56, 189, 248, 0.45)');
        gradient.addColorStop(0.65, 'rgba(56, 189, 248, 0.08)');
        gradient.addColorStop(1, 'rgba(56, 189, 248, 0.0)');
        return gradient;
      }

      let chartLabels = ['Feb 7', 'Feb 8', 'Feb 9', 'Feb 10', 'Feb 11', 'Feb 12'];
      let chartValues = [42, 38, 55, 78, 48, 62];

      try {
        const rawLabels = paymentsCanvas.getAttribute('data-labels');
        const rawValues = paymentsCanvas.getAttribute('data-values');
        if (rawLabels && rawValues) {
          const parsedL = JSON.parse(rawLabels);
          const parsedV = JSON.parse(rawValues);
          if (Array.isArray(parsedL) && parsedL.length > 0) {
            chartLabels = parsedL;
            chartValues = parsedV;
          }
        }
      } catch (e) {
        console.warn('Failed parsing rental payments chart data', e);
      }

      paymentsChart = new Chart(ctxPayments, {
        type: 'line',
        data: {
          labels: chartLabels,
          datasets: [{
            label: 'Rental Payments',
            data: chartValues,
            borderColor: '#38BDF8',
            borderWidth: 3,
            fill: true,
            backgroundColor: (context) => {
              const chart = context.chart;
              const { ctx, chartArea } = chart;
              if (!chartArea) return 'rgba(56, 189, 248, 0.2)';
              return getGradient(ctx, chartArea.bottom);
            },
            tension: 0.45,
            pointBackgroundColor: '#FFFFFF',
            pointBorderColor: '#38BDF8',
            pointBorderWidth: 2,
            pointRadius: 4,
            pointHoverRadius: 7
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              backgroundColor: isDark() ? '#0F172A' : '#111827',
              padding: 10,
              cornerRadius: 12,
              displayColors: false,
              callbacks: {
                label: function(context) {
                  return `${context.label}: $${Number(context.parsed.y).toLocaleString()}`;
                }
              }
            }
          },
          scales: {
            x: {
              grid: { display: false },
              ticks: { color: isDark() ? '#8492A6' : '#94A3B8' }
            },
            y: {
              border: { dash: [5, 5] },
              grid: { color: isDark() ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)' },
              ticks: {
                color: isDark() ? '#8492A6' : '#94A3B8',
                callback: (val) => `$${val}k`
              }
            }
          }
        }
      });
    }

    // 3. Total Revenue Bar Chart
    const revenueCanvas = document.getElementById('revenueBarChart');
    let revenueChart = null;

    if (revenueCanvas) {
      const ctxRev = revenueCanvas.getContext('2d');
      revenueChart = new Chart(ctxRev, {
        type: 'bar',
        data: {
          labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul'],
          datasets: [
            {
              label: 'Rental Income',
              data: [35, 45, 22, 38, 30, 60, 42],
              backgroundColor: '#38BDF8',
              borderRadius: 8,
              barThickness: 14
            },
            {
              label: 'Expenses',
              data: [15, 20, 10, 18, 14, 25, 18],
              backgroundColor: isDark() ? 'rgba(255,255,255,0.12)' : 'rgba(108, 76, 241, 0.15)',
              borderRadius: 8,
              barThickness: 14
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              backgroundColor: isDark() ? '#0F172A' : '#111827',
              cornerRadius: 10,
              padding: 10
            }
          },
          scales: {
            x: {
              grid: { display: false },
              ticks: { color: isDark() ? '#8492A6' : '#94A3B8' }
            },
            y: {
              border: { dash: [5, 5] },
              grid: { color: isDark() ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)' },
              ticks: {
                color: isDark() ? '#8492A6' : '#94A3B8',
                callback: (val) => `$${val}k`
              }
            }
          }
        }
      });
    }

    // Expose chart updater for instant theme toggles
    window.propioCharts = {
      updateTheme: (newTheme) => {
        const dark = newTheme === 'dark';
        const tickColor = dark ? '#8492A6' : '#94A3B8';
        const gridColor = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)';
        const tooltipBg = dark ? '#0F172A' : '#111827';

        if (unitsChart) {
          unitsChart.data.datasets[0].backgroundColor[2] = dark ? '#2D3748' : '#CFEBF1';
          if (unitsChart.options.plugins && unitsChart.options.plugins.tooltip) {
            unitsChart.options.plugins.tooltip.backgroundColor = tooltipBg;
          }
          unitsChart.update();
        }
        if (paymentsChart) {
          paymentsChart.options.scales.x.ticks.color = tickColor;
          paymentsChart.options.scales.y.ticks.color = tickColor;
          paymentsChart.options.scales.y.grid.color = gridColor;
          if (paymentsChart.options.plugins && paymentsChart.options.plugins.tooltip) {
            paymentsChart.options.plugins.tooltip.backgroundColor = tooltipBg;
          }
          paymentsChart.update();
        }
        if (revenueChart) {
          revenueChart.data.datasets[1].backgroundColor = dark ? 'rgba(255,255,255,0.14)' : 'rgba(108, 76, 241, 0.15)';
          revenueChart.options.scales.x.ticks.color = tickColor;
          revenueChart.options.scales.y.ticks.color = tickColor;
          revenueChart.options.scales.y.grid.color = gridColor;
          if (revenueChart.options.plugins && revenueChart.options.plugins.tooltip) {
            revenueChart.options.plugins.tooltip.backgroundColor = tooltipBg;
          }
          revenueChart.update();
        }
      }
    };
  }

  // ---------------------------------------------------------------------------
  // 5. DOM Initialization
  // ---------------------------------------------------------------------------
  document.addEventListener('DOMContentLoaded', () => {
    // Theme toggle button click
    const themeBtn = document.getElementById('theme-toggle-btn');
    if (themeBtn) {
      themeBtn.addEventListener('click', () => {
        const currentTheme = document.documentElement.getAttribute('data-bs-theme') || 'light';
        const nextTheme = currentTheme === 'dark' ? 'light' : 'dark';
        applyTheme(nextTheme);
      });
    }

    setupSidebar();
    animateCounters();
    setupCharts();

    // Search shortcut OS detection & Click to focus
    const isMac = (navigator.userAgentData?.platform || navigator.platform || '').toUpperCase().indexOf('MAC') >= 0;
    const kbdHint = document.getElementById('search-kbd-hint');
    const searchInput = document.getElementById('topbar-search-input');

    if (kbdHint) {
      kbdHint.textContent = isMac ? '⌘ + /' : 'Ctrl + /';
      kbdHint.title = `Click or press ${isMac ? '⌘ + /' : 'Ctrl + /'} to search`;
      kbdHint.style.cursor = 'pointer';
      kbdHint.addEventListener('click', () => {
        if (searchInput) {
          searchInput.focus();
          searchInput.select();
        }
      });
    }

    // Keyboard shortcut (⌘+/ or Ctrl+/) for search focus
    document.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === '/' || e.code === 'Slash')) {
        e.preventDefault();
        if (searchInput) {
          searchInput.focus();
          searchInput.select();
        }
      }
    });
    // Auto-dismiss bootstrap toasts
    const toastElements = document.querySelectorAll('.toast');
    toastElements.forEach(toastEl => {
      if (typeof bootstrap !== 'undefined' && bootstrap.Toast) {
        const bsToast = new bootstrap.Toast(toastEl, { delay: 4500, autohide: true });
        bsToast.show();
      }
    });

    // -------------------------------------------------------------------------
    // 6. Notification Bell Subsystem (Live Unread Count & Dropdown)
    // -------------------------------------------------------------------------
    function getCsrfHeaders() {
      const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
      const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
      const headers = { 'Content-Type': 'application/json' };
      if (token) {
        headers[header] = token;
      }
      return headers;
    }

    function initNotificationBell() {
      const badge = document.getElementById('notification-badge');
      const headerCount = document.getElementById('notification-header-count');
      const container = document.getElementById('notification-items-container');
      const markAllBtn = document.getElementById('mark-all-read-btn');
      const dropdownBtn = document.getElementById('notificationDropdownBtn');

      if (!badge || !container) return;

      function renderNotifications(data) {
        const unreadCount = data.unreadCount || 0;
        const items = data.notifications || [];

        if (unreadCount > 0) {
          badge.textContent = unreadCount > 99 ? '99+' : unreadCount;
          badge.style.display = 'inline-block';
        } else {
          badge.style.display = 'none';
        }

        if (headerCount) {
          headerCount.textContent = unreadCount;
        }

        if (items.length === 0) {
          container.innerHTML = `
            <div class="p-4 text-center text-muted fs-8">
              <i class="bi bi-bell-slash fs-4 d-block mb-1 text-muted"></i>
              No notifications
            </div>`;
          return;
        }

        let html = '<div class="list-group list-group-flush">';
        items.forEach(item => {
          const isUnread = !item.isRead;
          const bgClass = isUnread ? 'bg-primary-subtle bg-opacity-25' : '';
          const dot = isUnread ? '<span class="badge rounded-circle p-1 bg-primary me-2" style="width: 8px; height: 8px;"></span>' : '';
          
          html += `
            <div class="list-group-item list-group-item-action d-flex align-items-start gap-2 p-3 border-bottom ${bgClass}" 
                 style="cursor: pointer;" 
                 data-id="${item.id}" 
                 data-url="${item.linkUrl || ''}">
              <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 ${item.bgColor || 'bg-primary-subtle'}" style="width: 34px; height: 34px;">
                <i class="bi ${item.icon || 'bi-bell'} ${item.textColor || 'text-primary'} fs-7"></i>
              </div>
              <div class="flex-grow-1 min-w-0">
                <div class="d-flex align-items-center justify-content-between mb-1">
                  <div class="d-flex align-items-center">
                    ${dot}
                    <span class="fw-bold fs-8 text-truncate">${escapeHtml(item.title)}</span>
                  </div>
                  <small class="text-muted fs-9 ms-1">${escapeHtml(item.timeAgo)}</small>
                </div>
                <p class="text-muted fs-9 mb-0 text-wrap line-clamp-2">${escapeHtml(item.message)}</p>
              </div>
            </div>`;
        });
        html += '</div>';
        container.innerHTML = html;

        // Click handlers on items
        container.querySelectorAll('.list-group-item').forEach(el => {
          el.addEventListener('click', () => {
            const id = el.getAttribute('data-id');
            const targetUrl = el.getAttribute('data-url');

            // Mark as read in background
            fetch(`/api/notifications/${id}/read`, {
              method: 'POST',
              headers: getCsrfHeaders()
            }).then(() => {
              if (targetUrl && targetUrl !== '#' && targetUrl !== '') {
                window.location.href = targetUrl;
              } else {
                fetchNotifications();
              }
            }).catch(e => {
              if (targetUrl && targetUrl !== '#' && targetUrl !== '') {
                window.location.href = targetUrl;
              }
            });
          });
        });
      }

      function fetchNotifications() {
        fetch('/api/notifications')
          .then(res => {
            if (!res.ok) throw new Error('Unauthorized or error');
            return res.json();
          })
          .then(data => renderNotifications(data))
          .catch(() => {
            // Quiet fail if not logged in or endpoint blocked
          });
      }

      // Initial fetch on page load
      fetchNotifications();

      // Refresh on opening dropdown
      if (dropdownBtn) {
        dropdownBtn.addEventListener('show.bs.dropdown', fetchNotifications);
      }

      // Mark all read button handler
      if (markAllBtn) {
        markAllBtn.addEventListener('click', (e) => {
          e.preventDefault();
          e.stopPropagation();
          fetch('/api/notifications/read-all', {
            method: 'POST',
            headers: getCsrfHeaders()
          })
          .then(res => res.json())
          .then(() => {
            fetchNotifications();
          })
          .catch(err => console.warn('Failed marking notifications read', err));
        });
      }
    }

    // -------------------------------------------------------------------------
    // 7. Global Search Live Suggestions & Shortcut
    // -------------------------------------------------------------------------
    function initGlobalSearch() {
      const searchInput = document.getElementById('topbar-search-input');
      const suggestions = document.getElementById('search-suggestions');
      const content = document.getElementById('search-suggestions-content');
      const searchForm = document.getElementById('topbar-search-form');

      if (!searchInput || !suggestions || !content) return;

      let debounceTimer = null;

      function closeSuggestions() {
        suggestions.style.display = 'none';
      }

      searchInput.addEventListener('input', () => {
        clearTimeout(debounceTimer);
        const query = searchInput.value.trim();

        if (query.length < 2) {
          closeSuggestions();
          return;
        }

        debounceTimer = setTimeout(() => {
          fetch(`/api/search?q=${encodeURIComponent(query)}`)
            .then(res => res.json())
            .then(results => {
              if (!results || results.length === 0) {
                content.innerHTML = `
                  <div class="p-3 text-center text-muted fs-8">
                    <i class="bi bi-search fs-5 d-block mb-1 text-muted"></i>
                    No matches found for "${escapeHtml(query)}"
                  </div>`;
                suggestions.style.display = 'block';
                return;
              }

              let html = '<div class="list-group list-group-flush">';
              results.slice(0, 8).forEach(res => {
                html += `
                  <a href="${res.url}" class="list-group-item list-group-item-action d-flex align-items-center justify-content-between py-2 px-3 border-0 rounded-3 mb-1">
                    <div class="d-flex align-items-center gap-2 min-w-0">
                      <div class="rounded-circle d-flex align-items-center justify-content-center bg-light text-primary flex-shrink-0" style="width: 28px; height: 28px;">
                        <i class="bi ${res.icon || 'bi-search'} fs-8"></i>
                      </div>
                      <div class="text-truncate">
                        <div class="fw-semibold fs-8 text-truncate">${escapeHtml(res.title)}</div>
                        <small class="text-muted fs-9 text-truncate">${escapeHtml(res.subtitle)}</small>
                      </div>
                    </div>
                    <span class="badge ${res.badgeClass || 'bg-secondary-subtle text-secondary'} rounded-pill fs-9 ms-2 flex-shrink-0">${escapeHtml(res.badge)}</span>
                  </a>`;
              });
              html += `
                <div class="p-2 text-center border-top mt-1">
                  <a href="/search?q=${encodeURIComponent(query)}" class="fs-9 fw-semibold text-primary text-decoration-none">
                    View all results &rarr;
                  </a>
                </div>
              </div>`;
              content.innerHTML = html;
              suggestions.style.display = 'block';
            })
            .catch(() => closeSuggestions());
        }, 250);
      });

      // Close suggestions on outside click
      document.addEventListener('click', (e) => {
        if (!searchInput.contains(e.target) && !suggestions.contains(e.target)) {
          closeSuggestions();
        }
      });

      // Close on Escape
      document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
          closeSuggestions();
        }
      });
    }

    function escapeHtml(str) {
      if (!str) return '';
      return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }

    initNotificationBell();
    initGlobalSearch();
  });
})();
