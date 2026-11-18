const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

function run(command, args, options = {}) {
  execFileSync(command, args, { stdio: 'inherit', ...options });
}

function publishTo(repository, version) {
  const projects = ['aidl', 'shared', 'api', 'provider'];
  run('./gradlew', [
    '--configure-on-demand',
    ...projects.map((project) => `:${project}:publishAllPublicationsTo${repository}Repository`),
    `-PpublishVersion=${version}`
  ]);
}

function versionParts(version) {
  const match = /^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.-]+)?$/.exec(version);
  if (!match) throw new Error(`Unsupported release version: ${version}`);
  return match.slice(1).map(Number);
}

function updateManifest(version, stable) {
  const file = 'manifest.gradle';
  let manifest = fs.readFileSync(file, 'utf8');
  const next = versionParts(version);
  const fields = ['major', 'minor', 'patch'];
  const current = fields.map((field) => {
    const match = new RegExp(`\\bapi_version_${field}\\s*=\\s*(\\d+)\\b`).exec(manifest);
    if (!match) throw new Error(`Missing api_version_${field} in ${file}`);
    return Number(match[1]);
  });
  for (let i = 0; i < fields.length; i++) {
    if (next[i] > current[i]) break;
    if (next[i] < current[i]) {
      throw new Error(`Refusing to release ${version} below the version in ${file}`);
    }
  }
  if (stable) {
    fields.forEach((field, i) => {
      manifest = manifest.replace(
        new RegExp(`(\\bapi_version_${field}\\s*=\\s*)\\d+\\b`),
        `$1${next[i]}`
      );
    });
    fs.writeFileSync(file, manifest);
  }
}

exports.verifyConditions = async () => {
  const repository = process.env.MAVEN_REPOSITORY_DIR;
  if (!repository || !path.isAbsolute(repository)) {
    throw new Error('MAVEN_REPOSITORY_DIR must be an absolute path to the Maven branch checkout');
  }
  run('git', ['-C', repository, 'rev-parse', '--git-dir']);
  try {
    execFileSync('git', ['describe', '--tags', '--match', 'v[0-9]*', '--abbrev=0'], {
      stdio: 'pipe'
    });
  } catch (error) {
    throw new Error('No reachable version tag found. Import or create the last published vX.Y.Z tag before releasing.', { cause: error });
  }
};

exports.prepare = async (pluginConfig, { nextRelease, branch }) => {
  updateManifest(nextRelease.version, branch.name === 'main');
  publishTo('LocalRepo', nextRelease.version);
};

exports.publish = async (pluginConfig, { nextRelease }) => {
  publishTo('GitHubPackages', nextRelease.version);
  const repository = process.env.MAVEN_REPOSITORY_DIR;
  run('git', ['-C', repository, 'add', '--all']);
  run('git', ['-C', repository, 'commit', '-m', `Publish ${nextRelease.version}`]);
  run('git', ['-C', repository, 'push', 'origin', 'HEAD:maven']);
};
