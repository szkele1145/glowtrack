// Modrinth 上传脚本（Node 24 原生 fetch + FormData）
// 用法: node upload_modrinth.mjs
import fs from 'node:fs';
import path from 'node:path';

// 不要在这里写密钥：本仓库是公开的，历史提交无法撤回。
// 在 Modrinth 后台生成 token 后，用环境变量传入：
//   PowerShell:  $env:MODRINTH_TOKEN = 'mrp_...'; node upload_modrinth.mjs
const TOKEN = process.env.MODRINTH_TOKEN || '';
if (!TOKEN) {
  console.error('缺少环境变量 MODRINTH_TOKEN');
  process.exit(1);
}
const UA = 'glowtrack/1.0.0 (szkele1145)';
const API = 'https://api.modrinth.com/v2';
const JAR = 'D:/player-tracker/build/libs/glowtrack-1.0.0.jar';

// Xaero 前置依赖（可选）
const XAERO_WM = 'NcUtCpym';   // Xaero's World Map
const XAERO_MM = '1bokaNcj';   // Xaero's Minimap

async function req(url, opts = {}) {
  const res = await fetch(url, {
    ...opts,
    headers: { 'User-Agent': UA, 'Authorization': TOKEN, ...(opts.headers || {}) }
  });
  const text = await res.text();
  let body; try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, ok: res.ok, body };
}

// 1) 建项目（multipart 表单——该端点拒绝纯 JSON）
async function createProject() {
  const form = new FormData();
  form.append('slug', 'glowtrack');
  form.append('title', 'GlowTrack');
  form.append('description', 'Track player movement with glowing 3D trajectory lines, timeline review, teleport, and Xaero map integration.');
  form.append('body', '## GlowTrack\n\nTrack whitelisted players with a glowing rainbow heat-mapped trajectory rendered in-world, review the timeline, teleport to any moment, and overlay paths on Xaero maps.\n\n### Features\n- 3 render modes: 3D line, 2D flat line (high-altitude patrol), Xaero map overlay\n- Rainbow speed-heat coloring, glow, wallhack, time labels, flowing pulse\n- Timeline review with live readout + one-click teleport (cross-dimension)\n- OP GUI with searchable whitelist list\n\n### Dependencies\n- **NeoForge** 1.21.1 (required)\n- **Xaero\'s World Map** / **Minimap** (optional)\n\n### Source\n[GitHub](https://github.com/szkele1145/glowtrack)');
  form.append('game_versions', JSON.stringify(['1.21.1']));
  form.append('loaders', JSON.stringify(['neoforge']));
  form.append('license', 'arr');
  form.append('client_side', 'required');
  form.append('server_side', 'optional');
  form.append('categories', JSON.stringify(['utility']));
  form.append('is_draft', 'false');

  const res = await fetch(`${API}/project`, {
    method: 'POST',
    headers: { 'User-Agent': UA, 'Authorization': TOKEN },
    body: form
  });
  const text = await res.text();
  let body; try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, ok: res.ok, body };
}

// 2) 上传版本（multipart: data + file）
async function uploadVersion(projectId) {
  const data = {
    project_id: projectId,
    version_number: '1.0.0',
    version_title: 'GlowTrack 1.0.0',
    version_type: 'release',
    game_versions: ['1.21.1'],
    loaders: ['neoforge'],
    changelog: 'Initial release: player trajectory tracking with 3D/2D/Xaero render modes, timeline review, teleport, and Xaero world map + minimap overlay.',
    dependencies: [
      { project_id: XAERO_WM, dependency_type: 'optional' },
      { project_id: XAERO_MM, dependency_type: 'optional' }
    ],
    featured: true,
  };
  const form = new FormData();
  form.append('data', JSON.stringify(data));
  const bytes = fs.readFileSync(JAR);
  form.append('file', new Blob([bytes], { type: 'application/java-archive' }), path.basename(JAR));

  const res = await fetch(`${API}/version`, {
    method: 'POST',
    headers: { 'User-Agent': UA, 'Authorization': TOKEN },
    body: form
  });
  const text = await res.text();
  let body; try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, ok: res.ok, body };
}

async function main() {
  console.log('JAR size:', fs.statSync(JAR).size, 'bytes');

  // 先看项目是否已存在
  let projectId = null;
  const existing = await req(`${API}/project/glowtrack`);
  if (existing.ok) {
    projectId = existing.body.id;
    console.log('项目已存在 id=' + projectId);
  } else {
    console.log('项目不存在，创建中... (status ' + existing.status + ')');
    const created = await createProject();
    if (created.ok) {
      projectId = created.body.id;
      console.log('项目创建成功 id=' + projectId);
    } else {
      console.error('建项目失败 status=' + created.status, JSON.stringify(created.body));
      // 若因已存在(409)则再查一次
      if (created.status === 409) {
        const again = await req(`${API}/project/glowtrack`);
        if (again.ok) { projectId = again.body.id; console.log('实为已存在 id=' + projectId); }
      }
    }
  }

  if (!projectId) { console.error('无法获得 projectId，终止'); process.exit(1); }

  console.log('上传版本...');
  const up = await uploadVersion(projectId);
  if (up.ok) {
    console.log('版本上传成功！version id=' + up.body.id);
    console.log('URL: https://modrinth.com/mod/glowtrack/version/' + up.body.id);
  } else {
    console.error('版本上传失败 status=' + up.status, JSON.stringify(up.body));
    process.exit(1);
  }
}

main().catch(e => { console.error('ERR', e); process.exit(1); });
