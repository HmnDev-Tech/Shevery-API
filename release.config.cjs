module.exports = {
  branches: ['main', { name: 'dev', prerelease: true }],
  plugins: [
    '@semantic-release/commit-analyzer',
    '@semantic-release/release-notes-generator',
    './.github/scripts/release.cjs',
    ['@semantic-release/git', {
      assets: ['manifest.gradle'],
      message: 'chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}'
    }],
    ['@semantic-release/github', { successComment: false, failComment: false }]
  ]
};
