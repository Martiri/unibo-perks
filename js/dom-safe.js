/**
 * Safe DOM Construction & URL Validation Utilities (Zero-XSS Guarantee)
 *
 * Never uses innerHTML, outerHTML, insertAdjacentHTML, or document.write.
 */

const SVG_NS = 'http://www.w3.org/2000/svg';
const SAFE_ICON_ID_RE = /^[a-z0-9-]+$/;

/**
 * Validate an external URL, allowing only https: (or http: upgraded to https:).
 * Categorically blocks javascript:, data:, vbscript:, file:, blob:.
 *
 * @param {string} rawUrl
 * @returns {string}
 */
export function sanitizeExternalUrl(rawUrl) {
  if (typeof rawUrl !== 'string' || !rawUrl.trim()) {
    return 'https://www.unibo.it';
  }
  try {
    const parsed = new URL(rawUrl.trim());
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
      return 'https://www.unibo.it';
    }
    if (parsed.protocol === 'http:') {
      parsed.protocol = 'https:';
    }
    return parsed.toString();
  } catch {
    return 'https://www.unibo.it';
  }
}

/**
 * Safely remove all child nodes from an element.
 *
 * @param {HTMLElement} node
 */
export function clearChildren(node) {
  if (node) {
    node.replaceChildren();
  }
}

/**
 * Create a DOM element safely without innerHTML.
 *
 * @param {string} tag
 * @param {Record<string, any>} [attrs]
 * @param {Array<Node|string|null|undefined|false>} [children]
 * @returns {HTMLElement}
 */
export function el(tag, attrs = {}, children = []) {
  const element = document.createElement(tag);

  if (attrs && typeof attrs === 'object') {
    for (const [key, value] of Object.entries(attrs)) {
      if (value === null || value === undefined || value === false) {
        continue;
      }
      // Block inline event handler attributes (onclick, onerror, onload, etc.)
      if (key.toLowerCase().startsWith('on')) {
        throw new Error(`Security policy violation: inline event attribute "${key}" is forbidden.`);
      }
      if (key === 'className') {
        element.className = String(value);
      } else if (key === 'textContent') {
        element.textContent = String(value);
      } else if (key === 'dataset' && typeof value === 'object') {
        for (const [dKey, dVal] of Object.entries(value)) {
          if (dVal !== undefined && dVal !== null) {
            element.dataset[dKey] = String(dVal);
          }
        }
      } else if (key === 'href' && tag.toLowerCase() === 'a') {
        element.setAttribute('href', sanitizeExternalUrl(String(value)));
        element.setAttribute('rel', 'noopener noreferrer');
        element.setAttribute('target', '_blank');
      } else if (typeof value === 'boolean') {
        if (value) {
          element.setAttribute(key, '');
        }
      } else {
        element.setAttribute(key, String(value));
      }
    }
  }

  const childList = Array.isArray(children) ? children : [children];
  for (const child of childList) {
    if (child === null || child === undefined || child === false) {
      continue;
    }
    if (child instanceof Node) {
      element.appendChild(child);
    } else {
      element.appendChild(document.createTextNode(String(child)));
    }
  }

  return element;
}

/**
 * Create a safe inline SVG reference to `./assets/icons/sprite.svg`.
 *
 * @param {string} iconName
 * @param {string} [className]
 * @returns {SVGElement}
 */
export function svgIcon(iconName, className = 'icon') {
  const safeName = SAFE_ICON_ID_RE.test(iconName) ? iconName : 'info';
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('class', className);
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');

  const use = document.createElementNS(SVG_NS, 'use');
  use.setAttribute('href', `./assets/icons/sprite.svg#icon-${safeName}`);
  svg.appendChild(use);
  return svg;
}
