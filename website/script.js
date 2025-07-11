// Mobile Navigation Toggle
const hamburger = document.querySelector('.hamburger');
const navMenu = document.querySelector('.nav-menu');

hamburger.addEventListener('click', () => {
    hamburger.classList.toggle('active');
    navMenu.classList.toggle('active');
});

// Close mobile menu when clicking on a link
document.querySelectorAll('.nav-link').forEach(n => n.addEventListener('click', () => {
    hamburger.classList.remove('active');
    navMenu.classList.remove('active');
}));

// Smooth scrolling for navigation links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
        e.preventDefault();
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            target.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    });
});

// Video overlay functionality
const videoOverlay = document.getElementById('videoOverlay');
const playButton = document.getElementById('playButton');
const videoWrapper = document.querySelector('.video-wrapper iframe');

if (videoOverlay && playButton) {
    videoOverlay.addEventListener('click', () => {
        // Hide overlay
        videoOverlay.style.opacity = '0';
        setTimeout(() => {
            videoOverlay.style.display = 'none';
        }, 300);
        
        // Auto-play video (note: this requires the video URL to have autoplay parameter)
        if (videoWrapper) {
            const currentSrc = videoWrapper.src;
            if (!currentSrc.includes('autoplay=1')) {
                videoWrapper.src = currentSrc + (currentSrc.includes('?') ? '&' : '?') + 'autoplay=1';
            }
        }
    });
}

// Navbar background on scroll
window.addEventListener('scroll', () => {
    const navbar = document.querySelector('.navbar');
    if (window.scrollY > 100) {
        navbar.style.background = 'rgba(255, 255, 255, 0.98)';
        navbar.style.boxShadow = '0 2px 20px rgba(0, 0, 0, 0.1)';
    } else {
        navbar.style.background = 'rgba(255, 255, 255, 0.95)';
        navbar.style.boxShadow = 'none';
    }
});

// Intersection Observer for animations
const observerOptions = {
    threshold: 0.1,
    rootMargin: '0px 0px -50px 0px'
};

const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.style.opacity = '1';
            entry.target.style.transform = 'translateY(0)';
        }
    });
}, observerOptions);

// Observe elements for animation
document.addEventListener('DOMContentLoaded', () => {
    const animatedElements = document.querySelectorAll('.feature-card, .demo-step, .privacy-item');
    
    animatedElements.forEach(el => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(30px)';
        el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
        observer.observe(el);
    });
});

// Counter animation for hero stats
function animateCounters() {
    const counters = document.querySelectorAll('.stat-number');
    
    counters.forEach(counter => {
        const target = parseInt(counter.textContent.replace(/[^0-9]/g, ''));
        const duration = 2000; // 2 seconds
        const increment = target / (duration / 16); // 60 FPS
        let current = 0;
        
        const updateCounter = () => {
            if (current < target) {
                current += increment;
                if (counter.textContent.includes('K')) {
                    counter.textContent = Math.floor(current / 1000) + 'K+';
                } else if (counter.textContent.includes('%')) {
                    counter.textContent = Math.floor(current) + '%';
                } else {
                    counter.textContent = Math.floor(current) + '+';
                }
                requestAnimationFrame(updateCounter);
            } else {
                counter.textContent = counter.textContent; // Set final value
            }
        };
        
        updateCounter();
    });
}

// Trigger counter animation when hero section is visible
const heroObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            animateCounters();
            heroObserver.unobserve(entry.target);
        }
    });
}, { threshold: 0.5 });

const heroSection = document.querySelector('.hero-stats');
if (heroSection) {
    heroObserver.observe(heroSection);
}

// Download button functionality
document.querySelectorAll('.download-btn, .btn-primary').forEach(btn => {
    if (btn.href === '#' || btn.href.includes('#download')) {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            
            // Show coming soon message or redirect to app store
            if (btn.classList.contains('google-play')) {
                // Replace with actual Google Play Store URL when available
                alert('Coming soon to Google Play Store!\n\nThe app is currently in development. Please check back soon for the official release.');
            } else if (btn.classList.contains('direct-download')) {
                // Replace with actual APK download URL when available
                alert('Direct download will be available soon!\n\nPlease check back when the app is officially released.');
            } else {
                // Generic download message
                window.location.href = '#download';
            }
        });
    }
});

// Form validation (if contact forms are added later)
function validateEmail(email) {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return re.test(email);
}

// Performance optimization: Lazy loading for images
document.addEventListener('DOMContentLoaded', () => {
    const images = document.querySelectorAll('img[data-src]');
    
    const imageObserver = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const img = entry.target;
                img.src = img.dataset.src;
                img.classList.remove('lazy');
                imageObserver.unobserve(img);
            }
        });
    });
    
    images.forEach(img => imageObserver.observe(img));
});

// Console welcome message
console.log('%cWelcome to WiMap! 📡', 'color: #667eea; font-size: 20px; font-weight: bold;');
console.log('%cBuilding the world\'s most comprehensive WiFi network database while protecting user privacy.', 'color: #718096; font-size: 14px;');

// Add mobile-specific touch interactions
if ('ontouchstart' in window) {
    document.body.classList.add('touch-device');
    
    // Add tap highlights for better mobile UX
    const interactiveElements = document.querySelectorAll('.btn, .feature-card, .download-btn');
    interactiveElements.forEach(el => {
        el.style.webkitTapHighlightColor = 'rgba(102, 126, 234, 0.2)';
    });
}

// Service Worker registration for PWA (optional)
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
            .then(registration => {
                console.log('SW registered: ', registration);
            })
            .catch(registrationError => {
                console.log('SW registration failed: ', registrationError);
            });
    });
}