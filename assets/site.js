const I={
android:'<path d="M6 10a6 6 0 0 1 12 0zM8.5 4.6 7.2 2.6M15.5 4.6l1.3-2M9.5 7.4h.01M14.5 7.4h.01M6 11.8h12v6.4a1.4 1.4 0 0 1-1.4 1.3H7.4A1.4 1.4 0 0 1 6 18.2zM3.2 12.4v5M20.8 12.4v5M9.5 19.5v2.4M14.5 19.5v2.4"/>',
cal:'<path d="M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2zM4 10h16M8 3v4M16 3v4"/>',
search:'<path d="M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM20 20l-4-4"/>',
folder:'<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
timer:'<path d="M12 8v4l3 2M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zM9 2h6"/>',
mic:'<path d="M12 3a3 3 0 0 0-3 3v5a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM6 11a6 6 0 0 0 12 0M12 17v4M9 21h6"/>',
shield:'<path d="M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6zM9 12l2 2 4-4"/>',
nowifi:'<path d="M2 9a15 15 0 0 1 20 0M5.5 12.5a10 10 0 0 1 13 0M9 16a5 5 0 0 1 6 0M12 19.5h.01M4 4l16 16"/>',
home:'<path d="M4 11l8-7 8 7v9H4zM10 20v-6h4v6"/>',
help:'<path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7M12 17h.01"/>',
code:'<path d="M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16"/>',
sliders:'<path d="M4 7h10M18 7h2M4 17h2M10 17h10M14 4v6M6 14v6"/>',
moon:'<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>',
image:'<path d="M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M9 9h.01"/>',
doc:'<path d="M7 3h7l5 5v13H7zM14 3v5h5M10 13h6M10 17h6"/>',
zip:'<path d="M6 3h12v18H6zM12 3v4M12 9h2M10 11h2M12 13h2M10 15h2"/>',
up:'<path d="M12 19V5M5 12l7-7 7 7"/>',
eye:'<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>',
dl:'<path d="M12 4v11M7 11l5 5 5-5M5 20h14"/>'};
const svg=n=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${I[n].replace(/<path /g,'<path pathLength="1" ')}</svg>`;
document.querySelectorAll('[data-i]').forEach(e=>{e.classList.add('ic');e.innerHTML=svg(e.dataset.i)});
/* compact scroll-to-top control */
const ft=document.createElement('button');ft.className='ft';ft.type='button';ft.setAttribute('aria-label','Scroll to top');ft.innerHTML=svg('up');document.body.append(ft);
ft.onclick=()=>scrollTo({top:0,behavior:'smooth'});
addEventListener('scroll',()=>{if(scrollY>520)ft.classList.add('show');else ft.classList.remove('show')},{passive:true});
/* reveal svg icons */
const io=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target)}}),{threshold:.35});
document.querySelectorAll('.rv').forEach(e=>io.observe(e));
