# WiMap Landing Page

A modern, responsive landing page for the WiMap Android application, designed for deployment on CloudFlare Pages.

## Features

- **Modern Design**: Clean, professional design with gradient accents and smooth animations
- **Fully Responsive**: Optimized for desktop, tablet, and mobile devices
- **Performance Optimized**: Lightweight, fast-loading static website
- **SEO Ready**: Proper meta tags, structured content, and semantic HTML
- **CloudFlare Pages Ready**: Static HTML/CSS/JS, no server-side dependencies

## Structure

```
website/
├── index.html          # Main landing page
├── styles.css          # All styling and responsive design
├── script.js           # Interactive functionality and animations
├── app-ads.txt         # Google AdMob ads.txt file
├── favicon.ico         # Website favicon (placeholder)
└── README.md           # This file
```

## Key Sections

1. **Hero Section**: Eye-catching introduction with app mockup
2. **Features**: Detailed feature showcase with icons and descriptions
3. **Privacy**: GDPR compliance and privacy-first approach
4. **Demo**: YouTube video integration (requires video ID)
5. **Download**: App store links and download options
6. **Footer**: Legal links, support, and social media

## Customization Needed

### 1. YouTube Video
Replace `YOUR_VIDEO_ID` in index.html line with your actual YouTube video ID:
```html
<iframe src="https://www.youtube.com/embed/YOUR_ACTUAL_VIDEO_ID">
```

### 2. Download Links
Update the download button links when your app is published:
- Google Play Store URL
- Direct APK download URL

### 3. Favicon
Replace the placeholder favicon.ico with an actual favicon file representing your app.

### 4. Contact Information
Update email addresses and support links in the footer section.

## Deployment on CloudFlare Pages

1. **Upload Files**: Upload all files to your CloudFlare Pages project
2. **Set Build Settings**: 
   - Build command: (leave empty - static site)
   - Build output directory: `/`
3. **Custom Domain**: Configure your custom domain (e.g., wimap.app)
4. **SSL/Security**: CloudFlare automatically provides SSL certificates

## App-Ads.txt

The `app-ads.txt` file is included as required by Google AdMob:
- Contains your publisher ID: `pub-9891349918663384`
- Properly formatted for AdMob verification
- Accessible at: `https://yoursite.com/app-ads.txt`

## Performance Features

- **Lazy Loading**: Images load only when visible
- **Smooth Animations**: CSS transitions and JavaScript animations
- **Mobile Optimized**: Touch-friendly interactions
- **Fast Loading**: Optimized CSS and minimal JavaScript
- **SEO Friendly**: Proper meta tags and structured content

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)
- Mobile browsers (iOS Safari, Chrome Mobile)

## Legal Pages (To Add)

Consider adding these additional pages:
- `/privacy-policy.html` - Detailed privacy policy
- `/terms-of-service.html` - Terms of service
- `/gdpr.html` - GDPR compliance information
- `/faq.html` - Frequently asked questions
- `/help.html` - Help and support center

## Color Scheme

- Primary: `#667eea` (Blue)
- Secondary: `#764ba2` (Purple)
- Accent: `#f093fb` (Pink)
- Text: `#2d3748` (Dark Gray)
- Background: `#ffffff` (White)

## Typography

- Font Family: Inter (Google Fonts)
- Weights: 300, 400, 500, 600, 700, 800

The website is ready for deployment and will provide an excellent first impression for your WiMap application!