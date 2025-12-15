// Black cool particles background (no dependencies)
// - Fullscreen canvas behind UI
// - Particles + connecting lines
// - Mouse-driven attraction / subtle parallax

(function () {
    var canvas = document.getElementById('bg-canvas');
    if (!canvas) return;
    var ctx = canvas.getContext('2d');
    if (!ctx) return;

    var DPR = Math.min(window.devicePixelRatio || 1, 2);
    var W = 0, H = 0;
    var running = true;

    var mouse = { x: 0, y: 0, active: false };

    function resize() {
        DPR = Math.min(window.devicePixelRatio || 1, 2);
        W = Math.max(1, window.innerWidth || 1);
        H = Math.max(1, window.innerHeight || 1);
        canvas.width = Math.floor(W * DPR);
        canvas.height = Math.floor(H * DPR);
        canvas.style.width = W + 'px';
        canvas.style.height = H + 'px';
        ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    }

    function clamp(x, lo, hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    // Particle settings
    function getParticleCount() {
        var area = W * H;
        var base = Math.floor(area / 12000); // ~ 80 on 960k area
        return clamp(base, 60, 140);
    }

    var particles = [];

    function spawn() {
        var n = getParticleCount();
        particles = [];
        for (var i = 0; i < n; i++) {
            particles.push({
                x: Math.random() * W,
                y: Math.random() * H,
                vx: (Math.random() - 0.5) * 0.35,
                vy: (Math.random() - 0.5) * 0.35,
                r: 0.8 + Math.random() * 1.6,
                hue: 235 + Math.random() * 40, // purple/blue
                alpha: 0.35 + Math.random() * 0.35
            });
        }
    }

    function step() {
        if (!running) return;

        ctx.clearRect(0, 0, W, H);

        // subtle vignette
        var g = ctx.createRadialGradient(W * 0.5, H * 0.5, Math.min(W, H) * 0.1, W * 0.5, H * 0.5, Math.max(W, H) * 0.65);
        g.addColorStop(0, 'rgba(5,8,22,0.0)');
        g.addColorStop(1, 'rgba(5,8,22,0.85)');
        ctx.fillStyle = g;
        ctx.fillRect(0, 0, W, H);

        // Update & draw
        var linkDist = Math.min(170, Math.max(110, Math.sqrt(W * H) / 7));
        var linkDist2 = linkDist * linkDist;

        // Mouse attraction params
        var mx = mouse.x, my = mouse.y;
        var influence = mouse.active ? 1.0 : 0.0;
        var pullRadius = 220;
        var pullRadius2 = pullRadius * pullRadius;

        for (var i = 0; i < particles.length; i++) {
            var p = particles[i];

            // Mouse pull
            if (influence) {
                var dxm = mx - p.x;
                var dym = my - p.y;
                var dm2 = dxm * dxm + dym * dym;
                if (dm2 < pullRadius2 && dm2 > 0.0001) {
                    var dm = Math.sqrt(dm2);
                    var f = (1 - dm / pullRadius) * 0.018; // strength
                    p.vx += (dxm / dm) * f;
                    p.vy += (dym / dm) * f;
                }
            }

            // Drift + mild damping
            p.vx *= 0.985;
            p.vy *= 0.985;
            p.x += p.vx;
            p.y += p.vy;

            // Wrap around edges
            if (p.x < -20) p.x = W + 20;
            if (p.x > W + 20) p.x = -20;
            if (p.y < -20) p.y = H + 20;
            if (p.y > H + 20) p.y = -20;
        }

        // Links
        ctx.lineWidth = 1;
        for (var a = 0; a < particles.length; a++) {
            var pa = particles[a];
            for (var b = a + 1; b < particles.length; b++) {
                var pb = particles[b];
                var dx = pa.x - pb.x;
                var dy = pa.y - pb.y;
                var d2 = dx * dx + dy * dy;
                if (d2 > linkDist2) continue;
                var d = Math.sqrt(d2);
                var t = 1 - d / linkDist;
                var alpha = 0.12 * t;
                ctx.strokeStyle = 'rgba(168,85,247,' + alpha.toFixed(4) + ')';
                ctx.beginPath();
                ctx.moveTo(pa.x, pa.y);
                ctx.lineTo(pb.x, pb.y);
                ctx.stroke();
            }
        }

        // Dots
        for (var k = 0; k < particles.length; k++) {
            var pk = particles[k];
            ctx.fillStyle = 'hsla(' + pk.hue.toFixed(0) + ', 95%, 70%, ' + (pk.alpha * 0.9).toFixed(4) + ')';
            ctx.beginPath();
            ctx.arc(pk.x, pk.y, pk.r, 0, Math.PI * 2);
            ctx.fill();
        }

        requestAnimationFrame(step);
    }

    function onMouseMove(e) {
        mouse.active = true;
        mouse.x = e.clientX;
        mouse.y = e.clientY;
    }

    function onMouseLeave() {
        mouse.active = false;
    }

    function onVisibility() {
        running = !document.hidden;
        if (running) requestAnimationFrame(step);
    }

    // init
    resize();
    spawn();
    requestAnimationFrame(step);

    window.addEventListener('resize', function () {
        resize();
        spawn();
    });
    window.addEventListener('mousemove', onMouseMove, { passive: true });
    window.addEventListener('mouseleave', onMouseLeave, { passive: true });
    document.addEventListener('visibilitychange', onVisibility);
})();


