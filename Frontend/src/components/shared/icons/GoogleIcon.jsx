export function GoogleIcon({ style, ...props }) {
  return (
    <span
      aria-hidden="true"
      {...props}
      style={{
        width: '18px',
        height: '18px',
        borderRadius: '50%',
        display: 'inline-block',
        background:
          'conic-gradient(from -45deg, rgb(234, 67, 53) 0deg, rgb(234, 67, 53) 25%, rgb(251, 188, 5) 0deg, rgb(251, 188, 5) 50%, rgb(52, 168, 83) 0deg, rgb(52, 168, 83) 75%, rgb(66, 133, 244) 0deg)',
        ...style,
      }}
    />
  )
}
