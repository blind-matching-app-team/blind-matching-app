export function getPasswordRules(password: string) {
  const minLength = password.length >= 8;
  const hasLetter = /[A-Za-z]/.test(password);
  const hasNumber = /\d/.test(password);
  return { minLength, hasLetter, hasNumber, valid: minLength && hasLetter && hasNumber };
}
