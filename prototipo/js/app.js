const S=[{id:'c',n:'Corte masculino',ic:'✂️',p:35,m:30},{id:'b',n:'Barba',ic:'🧔',p:25,m:20},{id:'cb',n:'Corte + barba',ic:'🔥',p:55,m:50},{id:'pz',n:'Pezinho / acabamento',ic:'📏',p:15,m:10},{id:'sb',n:'Sobrancelha',ic:'👁️',p:10,m:10},{id:'ci',n:'Corte infantil',ic:'🧒',p:30,m:30},{id:'pl',n:'Platinado / luzes',ic:'⚡',p:120,m:90}];
const EX={cb:['c','b','ci'],c:['cb','ci'],ci:['c','cb','b'],b:['cb','ci']},MAXN=3,MAXM=200;
const P=[{id:'pm',n:'Pomada modeladora QOD',p:34},{id:'po',n:'Pomada modeladora Fox For Men',p:32},{id:'cr',n:'Creme pós-barba QOD',p:32},{id:'lo',n:'Loção pós-barba Barba Brava',p:29}];
const PR=[{n:'Gui',off:1},{n:'Rafa',off:3},{n:'Léo',off:5}];
const R=v=>'R$ '+v.toFixed(2).replace('.',',');
const $=(s,e=document)=>e.querySelector(s);
const pg=document.body.dataset.p;
const links=[['index','Início'],['servicos','Serviços'],['galeria','Galeria'],['info','Horários']];
document.body.insertAdjacentHTML('afterbegin',`<div class="pole"></div><header class="nav"><div><a href="index.html"><img src="img/logo.png" alt="BarberShop do Gui"></a><nav>${links.map(l=>`<a href="${l[0]}.html" class="${pg==l[0]?'on':''}">${l[1]}</a>`).join('')}</nav></div></header>`);
document.body.insertAdjacentHTML('beforeend',`<button class="btn fab" id="open">📅 Agendar</button><footer><div class="pole" style="max-width:200px;margin:0 auto 14px;border-radius:4px"></div><b>BARBERSHOP DO GUI</b><br>Zona Sul · São Paulo/SP (região do Grajaú) · Seg 11h–20h30 · Ter–Sáb 10h–20h30<br><a href="https://www.facebook.com/p/BarberShop-Do-Gui-100050410702068/" target="_blank" rel="noopener">Facebook</a> · Protótipo acadêmico FATEC · preços estimados</footer><div class="ov" id="ov"></div>`);
const card=s=>`<div class="card"><div class="ic">${s.ic}</div><h3>${s.n}</h3><small>⏱ ${s.m} min</small><div class="price">${R(s.p)}</div></div>`;
$('#sv')&&($('#sv').innerHTML=S.slice(0,pg=='index'?4:9).map(card).join(''));
$('#pd')&&($('#pd').innerHTML=P.map(p=>`<div class="card"><div class="ic">🧴</div><h3>${p.n}</h3><div class="price">${R(p.p)} <i>reserva e retirada</i></div></div>`).join(''));
const IM=[['fachada','Fachada'],['interior','Salão'],['corte-nuca','Degradê'],['corte-risco','Risquinho']];
$('#gl')&&($('#gl').innerHTML=IM.slice(0,pg=='index'?3:9).map(i=>`<button data-i="${i[0]}" aria-label="Ampliar ${i[1]}"><img src="img/${i[0]}.jpg" alt="${i[1]}" loading="lazy"></button>`).join(''));
const ov=$('#ov');const close=()=>{ov.className='ov';ov.innerHTML=''};
ov.onclick=e=>{if(e.target==ov)close()};document.onkeydown=e=>{if(e.key=='Escape')close()};
document.addEventListener('click',e=>{const b=e.target.closest('.gal button');if(b){ov.className='ov on lb';ov.innerHTML=`<img src="img/${b.dataset.i}-full.jpg" alt="">`}});
/* ---- agendamento ---- */
let st,step;const LBL=['Serviços','Profissional','Produto','Seus dados','Resumo'];
const tot=()=>st.sv.reduce((a,s)=>a+s.m,0),val=()=>st.sv.reduce((a,s)=>a+s.p,0);
const mon=o=>{const d=new Date();d.setHours(0,0,0,0);d.setDate(d.getDate()-((d.getDay()+6)%7)+o*7);return d};
const hrs=d=>[d.getDay()==1?11:10,20.5];
const busy=(p,d,t)=>{let h=(p*131+d.getDate()*17+d.getMonth()*7+t*23)%100;return h<38};
function slots(p,d){const [a,z]=hrs(d),need=Math.ceil(tot()/30),out=[],now=Date.now()+36e5;
 if(d.getDay()==PR[p].off)return out;
 for(let t=a;t+need*.5<=z;t+=.5){let ok=true;for(let k=0;k<need;k++)if(busy(p,d,t+k*.5))ok=false;
  const x=new Date(d);x.setHours(Math.floor(t),t%1?30:0);if(ok&&x>now)out.push(t)}return out}
const days=o=>[0,1,2,3,4,5].map(i=>{const d=mon(o);d.setDate(d.getDate()+i);return d});
const weekOf=p=>[0,1].find(o=>days(o).some(d=>slots(p,d).length))??1;
const fd=d=>['Dom','Seg','Ter','Qua','Qui','Sex','Sáb'][d.getDay()]+' '+d.toLocaleDateString('pt-BR',{day:'2-digit',month:'2-digit'});
const ft=t=>String(Math.floor(t)).padStart(2,'0')+':'+(t%1?'30':'00');
function open(){st={sv:[],pro:null,day:null,t:null,prod:[],ask:null,name:'',email:''};step=0;ov.className='ov on';draw()}
$('#open').onclick=open;document.addEventListener('click',e=>{if(e.target.closest('[data-open]'))open()});
function draw(){let b='',ok=false;
 if(step==0){const m=tot();b=`<h4>✂️ Qual serviço deseja?</h4>`+S.map(s=>{const sel=st.sv.includes(s),bad=!sel&&(st.sv.some(x=>(EX[x.id]||[]).includes(s.id))||st.sv.length>=MAXN||m+s.m>MAXM);return `<button class="opt ${sel?'s':''}" data-s="${s.id}" ${bad?'disabled':''}><span style="font-size:1.5rem">${s.ic}</span><b>${s.n}</b><span class="m">${R(s.p)}<br>⏱ ${s.m} min</span></button>`}).join('')+`<p class="hint">Até ${MAXN} serviços e ${MAXM} min por visita. Combos bloqueiam itens repetidos.</p><div class="bar"><span>⏱ ${m} min · <b>${R(val())}</b></span></div>`;ok=st.sv.length}
 if(step==1){const w=st.pro!=null?weekOf(st.pro):0;b=`<h4>💈 Escolha o profissional</h4><div class="chips">${PR.map((p,i)=>`<button class="chip ${st.pro===i?'s':''}" data-p="${i}">${p.n}</button>`).join('')}</div>`;
  if(st.pro!=null){const o=weekOf(st.pro);b+=`<p class="hint ${o?'warn':''}">${o?'⚠️ Semana atual cheia — liberamos a próxima semana.':'Agenda da semana atual.'}</p><div class="chips">${days(o).map((d,i)=>`<button class="chip ${st.day==i+'|'+o?'s':''}" data-d="${i}|${o}" ${slots(st.pro,d).length?'':'disabled'}>${fd(d)}</button>`).join('')}</div>`;
   if(st.day){const[i,oo]=st.day.split('|');b+=`<div class="chips">${slots(st.pro,days(+oo)[+i]).map(t=>`<button class="chip ${st.t===t?'s':''}" data-t="${t}">${ft(t)}</button>`).join('')}</div>`}}ok=st.t!=null}
 if(step==2){b=`<h4>🧴 Quer reservar algum produto?</h4><div class="row" style="margin:0 0 14px"><button class="btn ${st.ask===true?'':'ghost'}" data-y="1">Sim</button><button class="btn ${st.ask===false?'':'ghost'}" data-y="0">Não</button></div>`;
  if(st.ask)b+=P.map(p=>`<button class="opt ${st.prod.includes(p)?'s':''}" data-pr="${p.id}"><span style="font-size:1.4rem">🧴</span><b>${p.n}</b><span class="m">${R(p.p)}</span></button>`).join('')+'<p class="hint">Reserva para retirar e pagar na barbearia.</p>';ok=st.ask===false||(st.ask&&st.prod.length)}
 if(step==3){b=`<h4>📧 Seus dados</h4><label>Nome<input id="nm" placeholder="Seu nome" value="${st.name}" minlength="3"></label><label>E-mail<input id="em" type="email" placeholder="voce@email.com" value="${st.email}"></label><p class="hint">Enviaremos um link mágico para confirmar. Sem senha.</p>`;ok=st.name.trim().length>2&&/^\S+@\S+\.\S+$/.test(st.email)}
 if(step==4){const[i,o]=st.day.split('|'),d=days(+o)[+i],pv=st.prod.reduce((a,p)=>a+p.p,0);
  b=`<h4>📋 Confira seu pedido</h4><table class="sum">${st.sv.map(s=>`<tr><td>${s.ic} ${s.n} <small class="hint">${s.m} min</small></td><td>${R(s.p)}</td></tr>`).join('')}${st.prod.map(p=>`<tr><td>🧴 ${p.n}</td><td>${R(p.p)}</td></tr>`).join('')}<tr class="t"><td>Total</td><td>${R(val()+pv)}</td></tr></table><p>💈 <b>${PR[st.pro].n}</b> · 📅 ${fd(d)} às <b>${ft(st.t)}</b> (${tot()} min)<br>👤 ${st.name}<br>📧 ${st.email}</p><p class="hint">O horário só é reservado depois que você clicar no link enviado ao seu e-mail.<br>Cancelar: até 1 dia antes. Falta ou atraso de 30 min bloqueia o cadastro.</p>`;ok=1}
 if(step==5){b=`<h4>📨 Quase lá!</h4><p>Enviamos um link mágico para <b>${st.email}</b>. Abra-o para <b>confirmar</b> e finalizar o cadastro — só então o horário fica reservado.</p><p class="hint warn">Protótipo: nenhum e-mail foi enviado de verdade.</p><div class="bar"><button class="btn" data-x="1">Fechar</button></div>`}
 const nav=step<5?`<div class="bar"><button class="btn ghost" data-b="1" ${step?'':'disabled'}>← Voltar</button><button class="btn" data-n="1" ${ok?'':'disabled'}>${step==4?'Confirmar ✔':'Continuar →'}</button></div>`:'';
 ov.innerHTML=`<div class="modal"><div class="mh"><h3>${step<5?LBL[step]:'Pedido enviado'}</h3><button class="x" data-x="1" aria-label="Fechar">×</button></div><div class="prog">${LBL.map((_,i)=>`<i class="${i<step?'d':i==step?'c':''}"></i>`).join('')}</div><div class="mb">${b}${nav}</div></div>`;
 const m=ov.querySelector('.modal');m.onclick=e=>{const t=e.target.closest('button');if(!t)return;const D=t.dataset;
  if(D.x)return close();if(D.b){step--;return draw()}if(D.n){step++;return draw()}
  if(D.s){const s=S.find(x=>x.id==D.s),i=st.sv.indexOf(s);i<0?st.sv.push(s):st.sv.splice(i,1);st.pro=st.day=st.t=null}
  if(D.p){st.pro=+D.p;st.day=st.t=null}if(D.d){st.day=D.d;st.t=null}if(D.t)st.t=+D.t;
  if(D.y)st.ask=D.y=='1',st.prod=st.ask?st.prod:[];if(D.pr){const p=P.find(x=>x.id==D.pr),i=st.prod.indexOf(p);i<0?st.prod.push(p):st.prod.splice(i,1)}draw()};
 const nm=$('#nm'),em=$('#em');if(nm){nm.oninput=()=>{st.name=nm.value;chk()};em.oninput=()=>{st.email=em.value;chk()}}
 function chk(){m.querySelector('[data-n]').disabled=!(st.name.trim().length>2&&/^\S+@\S+\.\S+$/.test(st.email))}}
